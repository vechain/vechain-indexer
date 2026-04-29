#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="${SCRIPT_DIR}/../src/main/resources/token-registry"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
mkdir -p "${TARGET_DIR}"

log() {
    echo "[token-registry] $*"
}

fetch_registry() {
    local network="$1"
    local url="https://vechain.github.io/token-registry/${network}.json"
    local downloaded_file="${TMP_DIR}/${network}.json"
    local formatted_file="${TMP_DIR}/${network}.formatted.json"

    log "Fetching ${network}.json from ${url}"
    curl -fLsS "${url}" -o "${downloaded_file}"

    log "Validating ${network}.json structure"
    jq -e '
        if type != "array" then
            error("expected top-level array")
        elif any(
            .[];
            (.name | type != "string") or
            (.symbol | type != "string") or
            (.decimals | type != "number") or
            (.address | type != "string") or
            (.desc | type != "string") or
            (.icon | type != "string") or
            (.totalSupply | type != "string")
        ) then
            error("missing or invalid required token fields")
        else
            .
        end
    ' "${downloaded_file}" > /dev/null

    log "Formatting and writing ${network}.json to ${TARGET_DIR}"
    jq --sort-keys '.' "${downloaded_file}" > "${formatted_file}"
    mv "${formatted_file}" "${TARGET_DIR}/${network}.json"
}

log "Refreshing bundled token registry files"
fetch_registry "main"
fetch_registry "test"
log "Token registry refresh complete"
