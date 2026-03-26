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
trap 'rm -f "$CONFIG_FILE" "${TEST_VALUES_FILE:-}"' EXIT

# Seed dynamic test values from the baseline API
echo "Seeding dynamic test values from baseline..." >&2
TEST_VALUES_FILE=$(mktemp /tmp/regression-test-values.XXXXXX.json)
python3 -c "
import json, urllib.request, sys

baseline = '${BASELINE_URL}'.rstrip('/')

# Load static test values
with open('${SCRIPT_DIR}/test_values.json') as f:
    tv = json.load(f)

# Fetch active validator IDs from the baseline
try:
    url = f'{baseline}/api/v1/validators?status=ACTIVE&page=0&size=5'
    req = urllib.request.Request(url, headers={'User-Agent': 'regression-seed/1.0'})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())
    ids = [v['id'] for v in data.get('data', []) if v.get('id')]
    if ids:
        print(f'  Fetched {len(ids)} active validator IDs', file=sys.stderr)
        tv.setdefault('path_overrides', {})
        for path in ['/api/v1/validators', '/api/v1/validators/{validatorId}', '/api/v1/validators/blocks/historic/{validator}']:
            tv['path_overrides'].setdefault(path, {})
        tv['path_overrides']['/api/v1/validators/{validatorId}']['validatorId'] = ids
        tv['path_overrides']['/api/v1/validators/blocks/historic/{validator}']['validator'] = ids[:2]
        tv['parameters']['validator'] = ids
        tv['parameters']['validatorId'] = ids
except Exception as e:
    print(f'  Warning: could not seed validator IDs: {e}', file=sys.stderr)

with open('${TEST_VALUES_FILE}', 'w') as f:
    json.dump(tv, f, indent=2)
" || cp "${SCRIPT_DIR}/test_values.json" "$TEST_VALUES_FILE"

echo "Running regression comparison" >&2
echo "  baseline:  ${BASELINE_URL}" >&2
echo "  candidate: ${CANDIDATE_URL}" >&2
echo "  spec: ${SPEC_URL}" >&2

python3 "${SCRIPT_DIR}/compare_from_spec.py" \
  --config-file "$CONFIG_FILE" \
  --test-values "$TEST_VALUES_FILE" \
  --spec-url "$SPEC_URL" \
  --timeout "$TIMEOUT" \
  "$@"
