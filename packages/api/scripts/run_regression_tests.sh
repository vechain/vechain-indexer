#!/usr/bin/env bash
set -euo pipefail

# Run regression comparison between a network's live (baseline) and dead (candidate) colours.
#
# Fetches the OpenAPI spec, generates test cases for every operation,
# and compares responses from both endpoints.
#
# Environment variables:
#   NETWORK            - mainnet (default) or testnet; picks both URLs and the test values
#   BASELINE_URL       - Known-good reference endpoint (default: https://indexer.mainnet.vechain.org)
#   CANDIDATE_URL      - Candidate endpoint being validated (default: https://mainnet.dead.veworld.vechain.org)
#   SPEC_URL           - OpenAPI spec URL (default: derived from BASELINE_URL)
#   TIMEOUT            - Request timeout in seconds (default: 30)
#   ATTEMPTS           - Re-fetch a case while the baseline is still moving (default: 3)
#   RATE_LIMIT_BYPASS_TOKEN  - Sent as x-rate-limit-bypass so the WAF does not throttle the run
#   NUM_ABS_TOLERANCE  - Absolute numeric tolerance for leaf diffs (default: 1)
#   NUM_REL_TOLERANCE  - Relative numeric tolerance for leaf diffs (default: 0)
#                        Diffs within max(abs, rel*max(|a|,|b|)) are reported but
#                        do not fail the run.
#
# Usage:
#   packages/api/scripts/run_regression_tests.sh
#   packages/api/scripts/run_regression_tests.sh --output report.json
#   packages/api/scripts/run_regression_tests.sh --dry-run
#   NETWORK=testnet packages/api/scripts/run_regression_tests.sh --path-filter stargate
#   BASELINE_URL=https://custom.example.com packages/api/scripts/run_regression_tests.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Listing the suites needs neither a network nor a baseline.
for arg in "$@"; do
  if [[ "$arg" == "--list-suites" ]]; then
    exec python3 "${SCRIPT_DIR}/compare_from_spec.py" --list-suites
  fi
done

NETWORK="${NETWORK:-mainnet}"
network_field() { python3 "${SCRIPT_DIR}/networks.py" "$NETWORK" "$1"; }

BASELINE_URL="${BASELINE_URL:-$(network_field baseline)}"
CANDIDATE_URL="${CANDIDATE_URL:-$(network_field candidate)}"
NETWORK_VALUES="${SCRIPT_DIR}/$(network_field test_values)"
SPEC_URL="${SPEC_URL:-${BASELINE_URL}/api-docs}"
TIMEOUT="${TIMEOUT:-30}"
ATTEMPTS="${ATTEMPTS:-3}"
RATE_LIMIT_BYPASS_HEADER="${RATE_LIMIT_BYPASS_HEADER:-x-rate-limit-bypass}"
NUM_ABS_TOLERANCE="${NUM_ABS_TOLERANCE:-1}"
NUM_REL_TOLERANCE="${NUM_REL_TOLERANCE:-0}"

source "${SCRIPT_DIR}/cloudfront_only_guard.sh"
reject_direct_origin BASELINE_URL "$BASELINE_URL"
reject_direct_origin CANDIDATE_URL "$CANDIDATE_URL"

# Create temporary endpoints config
CONFIG_FILE=$(mktemp /tmp/regression-endpoints.XXXXXX.json)
cat > "$CONFIG_FILE" <<EOF
{
  "endpoints": {
    "baseline": "${BASELINE_URL}",
    "candidate": "${CANDIDATE_URL}"
  }
}
EOF
trap 'rm -f "$CONFIG_FILE" "${TEST_VALUES_FILE:-}"' EXIT

# Seed dynamic test values from the baseline API
echo "Seeding dynamic test values from baseline..." >&2
TEST_VALUES_FILE=$(mktemp /tmp/regression-test-values.XXXXXX.json)
seed_args=(
  --baseline-url "${BASELINE_URL}"
  --input "${SCRIPT_DIR}/test_values.json"
  --overlay "${NETWORK_VALUES}"
  --output "${TEST_VALUES_FILE}"
  --timeout 15
  --validator-sample-size "${VALIDATOR_SAMPLE_SIZE:-20}"
  --validator-page-size "${VALIDATOR_PAGE_SIZE:-20}"
  --validator-page-count "${VALIDATOR_PAGE_COUNT:-3}"
  --validator-seed "${VALIDATOR_SAMPLE_SEED:-1337}"
)

if [[ -n "${REGRESSION_SEED_METADATA_FILE:-}" ]]; then
  seed_args+=(--metadata-output "${REGRESSION_SEED_METADATA_FILE}")
fi

# A baseline that cannot be reached leaves the network's static values to stand alone.
python3 "${SCRIPT_DIR}/regression_seed.py" "${seed_args[@]}" \
  || python3 "${SCRIPT_DIR}/regression_seed.py" "${seed_args[@]}" --merge-only

echo "Running regression comparison" >&2
echo "  network:   ${NETWORK}" >&2
echo "  baseline:  ${BASELINE_URL}" >&2
echo "  candidate: ${CANDIDATE_URL}" >&2
echo "  spec: ${SPEC_URL}" >&2
echo "  attempts: ${ATTEMPTS}" >&2
echo "  numeric tolerance: abs=${NUM_ABS_TOLERANCE}, rel=${NUM_REL_TOLERANCE}" >&2

extra_args=(--candidate-spec-url "${CANDIDATE_SPEC_URL:-${CANDIDATE_URL}/api-docs}")
if [[ -n "${RATE_LIMIT_BYPASS_TOKEN:-}" ]]; then
  extra_args+=(--headers "$(printf '{"%s": "%s"}' "$RATE_LIMIT_BYPASS_HEADER" "$RATE_LIMIT_BYPASS_TOKEN")")
fi

python3 "${SCRIPT_DIR}/compare_from_spec.py" \
  --config-file "$CONFIG_FILE" \
  "${extra_args[@]}" \
  --test-values "$TEST_VALUES_FILE" \
  --spec-url "$SPEC_URL" \
  --timeout "$TIMEOUT" \
  --attempts "$ATTEMPTS" \
  --num-abs-tolerance "$NUM_ABS_TOLERANCE" \
  --num-rel-tolerance "$NUM_REL_TOLERANCE" \
  "$@"
