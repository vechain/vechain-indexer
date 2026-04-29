#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DUMP_SCRIPT="${SCRIPT_DIR}/restore_local_dump.sh"
readonly PRESET_PREFIX="MONGO_PRESET_"
readonly ENV_FILE="${SCRIPT_DIR}/.env"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

SOURCE_PRESET=""
SOURCE_URI=""
DESTINATION_PRESET=""
DESTINATION_URI=""
COLLECTIONS=""
WITH_BACKUP=0
RUN_DIR=""
NON_INTERACTIVE=0

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Interactive wrapper around restore_local_dump.sh that copies MongoDB
collections from a source database to a destination database.

Presets are environment variables prefixed with ${PRESET_PREFIX}, e.g.
  export MONGO_PRESET_LOCAL='mongodb://root:password@localhost:27017/vechain?authSource=admin'
  export MONGO_PRESET_GREEN_MAINNET='mongodb+srv://user@host.example.net/vechain?retryWrites=true&w=majority'

URIs that omit the password trigger a single hidden prompt. Passwords are
never written to disk and are passed to the underlying script via env vars.

Options:
  --source-preset NAME         Use the ${PRESET_PREFIX}<NAME> URI as source.
  --source-uri URI             Use a custom source URI.
  --destination-preset NAME    Use the ${PRESET_PREFIX}<NAME> URI as destination.
  --destination-uri URI        Use a custom destination URI.
  --collections name1,name2    Collections to copy (comma-separated).
  --with-backup                Run backup-destination before restore (off by default).
  --run-dir DIR                Override the run directory.
  --non-interactive            Fail instead of prompting for missing inputs.
  -h, --help                   Show this help.
EOF
}

die() {
  echo "Error: $*" >&2
  exit 1
}

preset_var_name() {
  printf '%s%s' "${PRESET_PREFIX}" "$1"
}

list_preset_names() {
  compgen -A variable 2>/dev/null | grep "^${PRESET_PREFIX}" | sed "s/^${PRESET_PREFIX}//" | sort
}

get_preset_uri() {
  local var_name
  var_name="$(preset_var_name "$1")"
  printf '%s' "${!var_name:-}"
}

mask_uri() {
  printf '%s' "$1" | sed -E 's#(mongodb(\+srv)?://)[^/@:]+(:[^/@]+)?@#\1****:****@#'
}

uri_encode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

extract_host_label() {
  local uri="$1"
  local without_scheme="${uri#mongodb://}"
  without_scheme="${without_scheme#mongodb+srv://}"
  local authority="${without_scheme%%/*}"
  authority="${authority%%\?*}"
  authority="${authority##*@}"
  [[ -n "${authority}" ]] || die "URI must include a host: $(mask_uri "${uri}")"
  printf '%s' "${authority}"
}

pick_uri_interactive() {
  local label="$1"
  local presets=()
  while IFS= read -r p; do
    [[ -n "${p}" ]] && presets+=("$p")
  done < <(list_preset_names)

  echo >&2
  echo "${label} MongoDB URI" >&2
  local i=1
  local p
  for p in "${presets[@]}"; do
    echo "  ${i}) ${p}: $(mask_uri "$(get_preset_uri "$p")")" >&2
    i=$((i+1))
  done
  echo "  ${i}) Enter custom URI" >&2

  local choice
  while true; do
    read -rp "Choose [1-${i}]: " choice
    if [[ "${choice}" =~ ^[0-9]+$ ]] && (( choice >= 1 && choice <= i )); then
      break
    fi
    echo "Invalid choice." >&2
  done

  if (( choice == i )); then
    local uri
    read -rp "${label} URI: " uri
    [[ -n "${uri}" ]] || die "${label} URI cannot be empty"
    printf '%s' "${uri}"
  else
    printf '%s' "$(get_preset_uri "${presets[$((choice-1))]}")"
  fi
}

ensure_uri_has_password() {
  local label="$1"
  local uri="$2"

  if [[ "${uri}" =~ ^mongodb(\+srv)?://[^/@]+:[^/@]*@ ]]; then
    printf '%s' "${uri}"
    return
  fi

  if [[ "${uri}" =~ ^(mongodb(\+srv)?://)([^/@:]+)@(.+)$ ]]; then
    local scheme="${BASH_REMATCH[1]}"
    local user="${BASH_REMATCH[3]}"
    local rest="${BASH_REMATCH[4]}"
    [[ "${NON_INTERACTIVE}" -eq 0 ]] || die "${label} URI requires a password and --non-interactive is set"
    local password
    printf 'Enter %s Mongo password (input hidden): ' "${label}" >&2
    IFS= read -r -s password
    echo >&2
    [[ -n "${password}" ]] || die "${label} password cannot be empty"
    printf '%s%s:%s@%s' "${scheme}" "${user}" "$(uri_encode "${password}")" "${rest}"
    return
  fi

  die "${label} URI must include at least a username (mongodb://user@host/db): $(mask_uri "${uri}")"
}

resolve_collections() {
  if [[ -n "${COLLECTIONS}" ]]; then
    return
  fi
  [[ "${NON_INTERACTIVE}" -eq 0 ]] || die "--collections is required with --non-interactive"
  local input
  read -rp "Collections (comma-separated): " input
  [[ -n "${input}" ]] || die "Collections list cannot be empty"
  COLLECTIONS="${input}"
}

default_run_dir() {
  printf '%s/runs/restore-%s' "${SCRIPT_DIR}" "$(date +%Y%m%d%H%M%S)"
}

resolve_uri() {
  local label="$1"
  local flag_prefix="$2"
  local preset="$3"
  local uri="$4"

  if [[ -n "${preset}" ]]; then
    uri="$(get_preset_uri "${preset}")"
    [[ -n "${uri}" ]] || die "${label} preset '${preset}' is not set ($(preset_var_name "${preset}") env var)"
  fi
  if [[ -z "${uri}" ]]; then
    [[ "${NON_INTERACTIVE}" -eq 0 ]] || die "${label} URI required (--${flag_prefix}-uri or --${flag_prefix}-preset) with --non-interactive"
    uri="$(pick_uri_interactive "${label}")"
  fi
  printf '%s' "${uri}"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --source-preset)      SOURCE_PRESET="${2:-}"; shift 2 ;;
      --source-uri)         SOURCE_URI="${2:-}"; shift 2 ;;
      --destination-preset) DESTINATION_PRESET="${2:-}"; shift 2 ;;
      --destination-uri)    DESTINATION_URI="${2:-}"; shift 2 ;;
      --collections)        COLLECTIONS="${2:-}"; shift 2 ;;
      --with-backup)        WITH_BACKUP=1; shift ;;
      --run-dir)            RUN_DIR="${2:-}"; shift 2 ;;
      --non-interactive)    NON_INTERACTIVE=1; shift ;;
      -h|--help)            usage; exit 0 ;;
      *)                    die "Unknown argument: $1" ;;
    esac
  done

  [[ -z "${SOURCE_PRESET}" || -z "${SOURCE_URI}" ]] || \
    die "--source-preset and --source-uri are mutually exclusive"
  [[ -z "${DESTINATION_PRESET}" || -z "${DESTINATION_URI}" ]] || \
    die "--destination-preset and --destination-uri are mutually exclusive"
}

main() {
  parse_args "$@"

  [[ -x "${DUMP_SCRIPT}" ]] || die "${DUMP_SCRIPT} not found or not executable"

  SOURCE_URI="$(resolve_uri "Source" "source" "${SOURCE_PRESET}" "${SOURCE_URI}")"
  DESTINATION_URI="$(resolve_uri "Destination" "destination" "${DESTINATION_PRESET}" "${DESTINATION_URI}")"
  resolve_collections

  SOURCE_URI="$(ensure_uri_has_password "source" "${SOURCE_URI}")"
  DESTINATION_URI="$(ensure_uri_has_password "destination" "${DESTINATION_URI}")"

  [[ -n "${RUN_DIR}" ]] || RUN_DIR="$(default_run_dir)"

  local destination_host
  destination_host="$(extract_host_label "${DESTINATION_URI}")"

  echo
  echo "Restore plan"
  echo "  Source:       $(mask_uri "${SOURCE_URI}")"
  echo "  Destination:  $(mask_uri "${DESTINATION_URI}")"
  echo "  Collections:  ${COLLECTIONS}"
  echo "  Backup:       $([[ ${WITH_BACKUP} -eq 1 ]] && echo yes || echo "no (pass --with-backup to enable)")"
  echo "  Run dir:      ${RUN_DIR}"
  echo
  if [[ "${NON_INTERACTIVE}" -eq 0 ]]; then
    local confirm
    read -rp "Proceed? [Y/n] (default: yes): " confirm
    case "${confirm}" in
      ""|y|Y|yes|Yes|YES) ;;
      *) die "Aborted" ;;
    esac
  fi

  export SOURCE_MONGO_URI="${SOURCE_URI}"
  export DESTINATION_MONGO_URI="${DESTINATION_URI}"

  echo
  echo "→ plan"
  "${DUMP_SCRIPT}" plan --collections "${COLLECTIONS}" --run-dir "${RUN_DIR}"

  echo
  echo "→ dump-source"
  "${DUMP_SCRIPT}" dump-source --collections "${COLLECTIONS}" --run-dir "${RUN_DIR}"

  if [[ "${WITH_BACKUP}" -eq 1 ]]; then
    echo
    echo "→ backup-destination"
    "${DUMP_SCRIPT}" backup-destination \
      --collections "${COLLECTIONS}" \
      --run-dir "${RUN_DIR}" \
      --yes \
      --confirm-target "${destination_host}"
  fi

  echo
  echo "→ restore"
  "${DUMP_SCRIPT}" restore \
    --collections "${COLLECTIONS}" \
    --run-dir "${RUN_DIR}" \
    --yes \
    --confirm-target "${destination_host}"

  echo
  echo "Done. Run dir: ${RUN_DIR}"
}

main "$@"
