#!/usr/bin/env bash
set -euo pipefail

# Run regression comparison between baseline and candidate endpoints.
#
# Fetches the OpenAPI spec, generates test cases for every operation,
# and compares responses from both endpoints.
#
# Environment variables:
#   BASELINE_URL       - Known-good reference endpoint (default: https://indexer.mainnet.vechain.org)
#   CANDIDATE_URL      - Candidate endpoint being validated (default: https://mainnet.dead.prod.veworld.vechain.org)
#   SPEC_URL           - OpenAPI spec URL (default: derived from BASELINE_URL)
#   TIMEOUT            - Request timeout in seconds (default: 30)
#   NUM_ABS_TOLERANCE  - Absolute numeric tolerance for leaf diffs (default: 1)
#   NUM_REL_TOLERANCE  - Relative numeric tolerance for leaf diffs (default: 0)
#                        Diffs within max(abs, rel*max(|a|,|b|)) are reported but
#                        do not fail the run.
#
# Usage:
#   packages/api/scripts/run_regression_tests.sh
#   packages/api/scripts/run_regression_tests.sh --output report.json
#   packages/api/scripts/run_regression_tests.sh --dry-run
#   packages/api/scripts/run_regression_tests.sh --path-filter "/api/v1/stargate.*"
#   BASELINE_URL=https://custom.example.com packages/api/scripts/run_regression_tests.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASELINE_URL="${BASELINE_URL:-https://indexer.mainnet.vechain.org}"
CANDIDATE_URL="${CANDIDATE_URL:-https://mainnet.dead.prod.veworld.vechain.org}"
SPEC_URL="${SPEC_URL:-${BASELINE_URL}/api-docs}"
TIMEOUT="${TIMEOUT:-30}"
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

python3 "${SCRIPT_DIR}/regression_seed.py" "${seed_args[@]}" \
  || cp "${SCRIPT_DIR}/test_values.json" "$TEST_VALUES_FILE"

echo "Running regression comparison" >&2
echo "  baseline:  ${BASELINE_URL}" >&2
echo "  candidate: ${CANDIDATE_URL}" >&2
echo "  spec: ${SPEC_URL}" >&2
echo "  numeric tolerance: abs=${NUM_ABS_TOLERANCE}, rel=${NUM_REL_TOLERANCE}" >&2

python3 "${SCRIPT_DIR}/compare_from_spec.py" \
  --config-file "$CONFIG_FILE" \
  --test-values "$TEST_VALUES_FILE" \
  --spec-url "$SPEC_URL" \
  --timeout "$TIMEOUT" \
  --num-abs-tolerance "$NUM_ABS_TOLERANCE" \
  --num-rel-tolerance "$NUM_REL_TOLERANCE" \
  "$@"
