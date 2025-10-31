#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="dead"
BASE_URL="${BASE_URL:-}"

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    dead|live)
      ENVIRONMENT="$1"
      shift
      ;;
    --base-url=*)
      BASE_URL="${1#*=}"
      shift
      ;;
    --base-url)
      if [[ $# -lt 2 ]]; then
        echo "--base-url requires a value" >&2
        exit 2
      fi
      BASE_URL="$2"
      shift 2
      ;;
    http://*|https://*)
      BASE_URL="$1"
      shift
      ;;
    *)
      POSITIONAL+=("$1")
      shift
      ;;
  esac
done

if [[ ${#POSITIONAL[@]} -gt 0 ]]; then
  set -- "${POSITIONAL[@]}"
else
  set --
fi

if [[ -z "${BASE_URL}" ]]; then
  case "${ENVIRONMENT}" in
    dead)
      BASE_URL="https://mainnet.dead.prod.veworld.vechain.org"
      ;;
    live)
      BASE_URL="https://indexer.mainnet.vechain.org"
      ;;
    *)
      echo "Unsupported environment: ${ENVIRONMENT}. Provide --base-url to target a custom host." >&2
      exit 1
      ;;
  esac
fi

BASE_URL="${BASE_URL%/}"
SCHEMA_URL="${BASE_URL}/api-docs"

SCHEMATHESIS_BIN="${SCHEMATHESIS_BIN:-schemathesis}"
MAX_RESPONSE_MILLISECONDS="${MAX_RESPONSE_MILLISECONDS:-2000}"
HYPOTHESIS_MAX_EXAMPLES="${HYPOTHESIS_MAX_EXAMPLES:-200}"

if [[ ! "${MAX_RESPONSE_MILLISECONDS}" =~ ^[0-9]+$ ]]; then
  echo "MAX_RESPONSE_MILLISECONDS must be a whole number representing milliseconds" >&2
  exit 2
fi

if ! command -v "${SCHEMATHESIS_BIN}" >/dev/null 2>&1; then
  echo "${SCHEMATHESIS_BIN} is required but was not found in PATH." >&2
  exit 127
fi

echo "Running schema-driven tests against ${BASE_URL}" >&2

"${SCHEMATHESIS_BIN}" run "${SCHEMA_URL}" \
  --base-url="${BASE_URL}" \
  --hypothesis-derandomize \
  --hypothesis-max-examples="${HYPOTHESIS_MAX_EXAMPLES}" \
  --checks=status_code_conformance \
  --checks=not_a_server_error \
  --checks=content_type_conformance \
  --stateful=links \
  --max-response-time="${MAX_RESPONSE_MILLISECONDS}" \
  "$@"
