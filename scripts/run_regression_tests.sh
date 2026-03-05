#!/usr/bin/env bash
set -euo pipefail

# Run regression comparison between baseline and candidate endpoints.
#
# Fetches the OpenAPI spec, generates test cases for every operation,
# and compares responses from both endpoints.
#
# Environment variables:
#   BASELINE_URL  - Known-good reference endpoint (default: https://indexer.mainnet.vechain.org)
#   CANDIDATE_URL - Candidate endpoint being validated (default: https://mainnet.dead.prod.veworld.vechain.org)
#   SPEC_URL      - OpenAPI spec URL (default: derived from BASELINE_URL)
#   TIMEOUT       - Request timeout in seconds (default: 30)
#
# Usage:
#   scripts/run_regression_tests.sh
#   scripts/run_regression_tests.sh --output report.json
#   scripts/run_regression_tests.sh --dry-run
#   scripts/run_regression_tests.sh --path-filter "/api/v1/stargate.*"
#   BASELINE_URL=https://custom.example.com scripts/run_regression_tests.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASELINE_URL="${BASELINE_URL:-https://indexer.mainnet.vechain.org}"
CANDIDATE_URL="${CANDIDATE_URL:-https://mainnet.dead.prod.veworld.vechain.org}"
SPEC_URL="${SPEC_URL:-${BASELINE_URL}/api-docs}"
TIMEOUT="${TIMEOUT:-30}"

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
trap 'rm -f "$CONFIG_FILE"' EXIT

echo "Running regression comparison" >&2
echo "  baseline:  ${BASELINE_URL}" >&2
echo "  candidate: ${CANDIDATE_URL}" >&2
echo "  spec: ${SPEC_URL}" >&2

python3 "${SCRIPT_DIR}/compare_from_spec.py" \
  --config-file "$CONFIG_FILE" \
  --test-values "${SCRIPT_DIR}/test_values.json" \
  --spec-url "$SPEC_URL" \
  --timeout "$TIMEOUT" \
  "$@"
