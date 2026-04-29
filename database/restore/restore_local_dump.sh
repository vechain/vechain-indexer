#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_NAME="$(basename "$0")"
readonly SCRIPT_PATH="$0"
readonly SCRIPT_DIR_LOCAL_DUMP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DEFAULT_RUN_ROOT="${SCRIPT_DIR_LOCAL_DUMP}/runs"
readonly REQUIRED_DATABASE="vechain"
readonly MANIFEST_NAME="manifest.txt"

SELECTED_COLLECTIONS=()
SOURCE_MONGO_URI="${SOURCE_MONGO_URI:-}"
DESTINATION_MONGO_URI="${DESTINATION_MONGO_URI:-}"
RUN_DIR=""
YES=0
CONFIRM_TARGET=""
PROMPT_SOURCE_PASSWORD=0
PROMPT_DESTINATION_PASSWORD=0
NO_INDEX_RESTORE=0
SUBCOMMAND="${1:-}"

usage() {
  cat <<EOF
Usage:
  ${SCRIPT_NAME} plan --source-uri URI --destination-uri URI --collections name1,name2 [--run-dir DIR]
  ${SCRIPT_NAME} dump-source --source-uri URI --collections name1,name2 [--run-dir DIR] [--prompt-source-password]
  ${SCRIPT_NAME} backup-destination --destination-uri URI --collections name1,name2 [--run-dir DIR] [--prompt-destination-password] --yes --confirm-target EXPECTED_DESTINATION_HOST
  ${SCRIPT_NAME} restore --destination-uri URI --run-dir DIR --collections name1,name2 [--prompt-destination-password] [--no-index-restore] --yes --confirm-target EXPECTED_DESTINATION_HOST

Environment fallback:
  SOURCE_MONGO_URI
  DESTINATION_MONGO_URI

Notes:
  - --collections is required for every subcommand and must be a comma-separated list of collection names.
  - The database in both URIs must be '${REQUIRED_DATABASE}'.
  - The restore step reads dumps already present under the selected run directory.
  - Use --prompt-source-password / --prompt-destination-password when the URI contains a username but omits the password.
  - Use --no-index-restore to skip restoring indexes from the source dump (by default indexes are restored).
EOF
}

die() {
  echo "Error: $*" >&2
  exit 1
}

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
}

run_with_log() {
  local log_file="$1"
  shift
  "$@" 2>&1 | tee "${log_file}"
}

mask_uri() {
  printf '%s' "$1" | sed -E 's#(mongodb(\+srv)?://)[^/@:]+(:[^/@]+)?@#\1****:****@#'
}

prompt_password() {
  local label="$1"
  local password

  printf 'Enter %s Mongo password (input hidden): ' "${label}" >&2
  IFS= read -r -s password
  echo >&2
  [[ -n "${password}" ]] || die "${label} Mongo password cannot be empty"
  printf '%s' "${password}"
}

uri_encode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

uri_has_username_without_password() {
  local uri="$1"
  [[ "${uri}" =~ ^mongodb(\+srv)?://[^/@:]+@ ]]
}

inject_password_into_uri() {
  local uri="$1"
  local password="$2"

  if [[ "${uri}" =~ ^(mongodb(\+srv)?://)([^/@:]+)@(.+)$ ]]; then
    printf '%s%s:%s@%s' \
      "${BASH_REMATCH[1]}" \
      "${BASH_REMATCH[3]}" \
      "$(uri_encode "${password}")" \
      "${BASH_REMATCH[4]}"
    return 0
  fi

  die "URI must include a username and omit only the password to use prompting: $(mask_uri "${uri}")"
}

resolve_prompted_passwords() {
  if [[ "${PROMPT_SOURCE_PASSWORD}" -eq 1 ]]; then
    [[ -n "${SOURCE_MONGO_URI}" ]] || die "--source-uri or SOURCE_MONGO_URI is required with --prompt-source-password"
    uri_has_username_without_password "${SOURCE_MONGO_URI}" || \
      die "--prompt-source-password requires a source URI in the form mongodb://user@host/db"
    SOURCE_MONGO_URI="$(inject_password_into_uri "${SOURCE_MONGO_URI}" "$(prompt_password "source")")"
  fi

  if [[ "${PROMPT_DESTINATION_PASSWORD}" -eq 1 ]]; then
    [[ -n "${DESTINATION_MONGO_URI}" ]] || die "--destination-uri or DESTINATION_MONGO_URI is required with --prompt-destination-password"
    uri_has_username_without_password "${DESTINATION_MONGO_URI}" || \
      die "--prompt-destination-password requires a destination URI in the form mongodb://user@host/db"
    DESTINATION_MONGO_URI="$(inject_password_into_uri "${DESTINATION_MONGO_URI}" "$(prompt_password "destination")")"
  fi
}

normalize_uri_for_docker() {
  printf '%s' "$1" | sed -E 's#(mongodb(\+srv)?://)([^/]*@)?(localhost|127\.0\.0\.1)([:/])#\1\3host.docker.internal\5#'
}

extract_database_name() {
  local uri="$1"
  local without_query="${uri%%\?*}"
  local db_name="${without_query##*/}"
  if [[ -z "${db_name}" || "${db_name}" == "${without_query}" ]]; then
    die "Mongo URI must include a database name: $(mask_uri "${uri}")"
  fi
  printf '%s' "${db_name}"
}

extract_host_label() {
  local uri="$1"
  local without_scheme="${uri#mongodb://}"
  without_scheme="${without_scheme#mongodb+srv://}"
  local authority="${without_scheme%%/*}"
  authority="${authority%%\?*}"
  authority="${authority##*@}"

  [[ -n "${authority}" ]] || die "Mongo URI must include a host: $(mask_uri "${uri}")"
  printf '%s' "${authority}"
}

timestamp() {
  date '+%Y%m%d%H%M%S'
}

default_run_dir() {
  printf '%s/%s' "${DEFAULT_RUN_ROOT}" "$(timestamp)"
}

ensure_dir() {
  mkdir -p "$1"
}

absolute_path() {
  python3 -c 'import os, sys; print(os.path.abspath(sys.argv[1]))' "$1"
}

manifest_path() {
  printf '%s/%s' "${RUN_DIR}" "${MANIFEST_NAME}"
}

append_manifest() {
  local key="$1"
  local collection="$2"
  local value="$3"
  printf '%s|%s|%s\n' "${key}" "${collection}" "${value}" >>"$(manifest_path)"
}

get_manifest_value() {
  local key="$1"
  local collection="$2"
  local manifest
  manifest="$(manifest_path)"
  [[ -f "${manifest}" ]] || die "Manifest not found: ${manifest}"
  awk -F'|' -v wanted_key="${key}" -v wanted_collection="${collection}" \
    '$1 == wanted_key && $2 == wanted_collection { print $3; found = 1 } END { if (!found) exit 1 }' \
    "${manifest}" || die "Manifest entry not found for ${key}/${collection}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found in PATH: $1"
}

resolve_selected_collections() {
  [[ ${#SELECTED_COLLECTIONS[@]} -gt 0 ]] || die "--collections is required and must include at least one collection"

  local collection
  local validated=()
  for collection in "${SELECTED_COLLECTIONS[@]}"; do
    collection="$(printf '%s' "${collection}" | xargs)"
    [[ -n "${collection}" ]] || continue
    [[ "${collection}" =~ ^[A-Za-z0-9._-]+$ ]] || \
      die "Invalid collection name: ${collection}"
    validated+=("${collection}")
  done

  [[ ${#validated[@]} -gt 0 ]] || die "--collections must include at least one valid collection name"
  SELECTED_COLLECTIONS=("${validated[@]}")
}

require_supported_database() {
  local db_name="$1"
  [[ "${db_name}" == "${REQUIRED_DATABASE}" ]] || \
    die "Expected database '${REQUIRED_DATABASE}', got '${db_name}'"
}

require_confirmation() {
  local expected_target="$1"
  [[ "${YES}" -eq 1 ]] || die "This command requires --yes"
  [[ "${CONFIRM_TARGET}" == "${expected_target}" ]] || \
    die "--confirm-target must exactly match '${expected_target}'"
}

docker_mongo() {
  local mounted_run_dir="$1"
  shift
  docker run --rm \
    --add-host=host.docker.internal:host-gateway \
    -v "${mounted_run_dir}:/work" \
    -w /work \
    mongo:8 \
    "$@"
}

count_documents() {
  local mounted_run_dir="$1"
  local uri="$2"
  local db_name="$3"
  local collection="$4"
  local normalized_uri
  normalized_uri="$(normalize_uri_for_docker "${uri}")"

  docker_mongo "${mounted_run_dir}" \
    mongosh "${normalized_uri}" --quiet \
    --eval "const database = db.getSiblingDB('${db_name}'); print(database.getCollection('${collection}').countDocuments({}));" \
    | tr -d '[:space:]'
}

dump_collection() {
  local mounted_run_dir="$1"
  local uri="$2"
  local db_name="$3"
  local collection="$4"
  local output_dir="$5"
  local normalized_uri
  normalized_uri="$(normalize_uri_for_docker "${uri}")"

  docker_mongo "${mounted_run_dir}" \
    mongodump \
    --uri="${normalized_uri}" \
    --db="${db_name}" \
    --collection="${collection}" \
    --out="${output_dir}"
}

restore_collection() {
  local mounted_run_dir="$1"
  local uri="$2"
  local db_name="$3"
  local collection="$4"
  local normalized_uri
  local restore_file
  normalized_uri="$(normalize_uri_for_docker "${uri}")"
  restore_file="/work/source-dump/${db_name}/${collection}.bson"

  local index_flag=()
  if [[ "${NO_INDEX_RESTORE}" -eq 1 ]]; then
    index_flag=(--noIndexRestore)
  fi

  docker_mongo "${mounted_run_dir}" \
    mongorestore \
    --uri="${normalized_uri}" \
    --nsInclude="${db_name}.${collection}" \
    --drop \
    ${index_flag[@]+"${index_flag[@]}"} \
    "${restore_file}"
}

required_dump_path() {
  local collection="$1"
  printf '%s/source-dump/%s/%s.bson' "${RUN_DIR}" "${REQUIRED_DATABASE}" "${collection}"
}

parse_args() {
  shift || true
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --source-uri)
        SOURCE_MONGO_URI="${2:-}"
        shift 2
        ;;
      --destination-uri)
        DESTINATION_MONGO_URI="${2:-}"
        shift 2
        ;;
      --run-dir)
        RUN_DIR="${2:-}"
        shift 2
        ;;
      --collections)
        IFS=',' read -r -a SELECTED_COLLECTIONS <<<"${2:-}"
        shift 2
        ;;
      --yes)
        YES=1
        shift
        ;;
      --confirm-target)
        CONFIRM_TARGET="${2:-}"
        shift 2
        ;;
      --prompt-source-password)
        PROMPT_SOURCE_PASSWORD=1
        shift
        ;;
      --prompt-destination-password)
        PROMPT_DESTINATION_PASSWORD=1
        shift
        ;;
      --no-index-restore)
        NO_INDEX_RESTORE=1
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "Unknown argument: $1"
        ;;
    esac
  done
}

prepare_run_dir() {
  if [[ -z "${RUN_DIR}" ]]; then
    RUN_DIR="$(default_run_dir)"
  fi
  RUN_DIR="$(absolute_path "${RUN_DIR}")"
  ensure_dir "${RUN_DIR}"
  ensure_dir "${RUN_DIR}/logs"
}

write_manifest_header() {
  local manifest
  manifest="$(manifest_path)"
  if [[ ! -f "${manifest}" ]]; then
    {
      printf 'created_at|run|%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
      printf 'run_dir|run|%s\n' "${RUN_DIR}"
    } >"${manifest}"
  fi
}

log_target_summary() {
  local label="$1"
  local uri="$2"
  log "${label}: $(mask_uri "${uri}")"
  log "Collections: ${SELECTED_COLLECTIONS[*]}"
}

plan_command() {
  [[ -n "${SOURCE_MONGO_URI}" ]] || die "--source-uri or SOURCE_MONGO_URI is required"
  [[ -n "${DESTINATION_MONGO_URI}" ]] || die "--destination-uri or DESTINATION_MONGO_URI is required"

  prepare_run_dir

  local source_db destination_db
  local destination_target
  source_db="$(extract_database_name "${SOURCE_MONGO_URI}")"
  destination_db="$(extract_database_name "${DESTINATION_MONGO_URI}")"
  destination_target="$(extract_host_label "${DESTINATION_MONGO_URI}")"
  require_supported_database "${source_db}"
  require_supported_database "${destination_db}"

  cat <<EOF
Plan
  Run directory: ${RUN_DIR}
  Source URI: $(mask_uri "${SOURCE_MONGO_URI}")
  Destination URI: $(mask_uri "${DESTINATION_MONGO_URI}")
  Database: ${REQUIRED_DATABASE}
  Confirmation label for destructive steps: ${destination_target}

Collections
$(for collection in "${SELECTED_COLLECTIONS[@]}"; do printf '  - %s\n' "${collection}"; done)

Commands
  ${SCRIPT_PATH} dump-source --source-uri '$(mask_uri "${SOURCE_MONGO_URI}")' --run-dir '${RUN_DIR}'
  ${SCRIPT_PATH} backup-destination --destination-uri '$(mask_uri "${DESTINATION_MONGO_URI}")' --run-dir '${RUN_DIR}' --yes --confirm-target ${destination_target}
  ${SCRIPT_PATH} restore --destination-uri '$(mask_uri "${DESTINATION_MONGO_URI}")' --run-dir '${RUN_DIR}' --yes --confirm-target ${destination_target}

Underlying Mongo operations
$(for collection in "${SELECTED_COLLECTIONS[@]}"; do
  printf "  - dump %s: docker run ... mongodump --uri='%s' --db='%s' --collection='%s' --out=/work/source-dump\n" \
    "${collection}" "$(mask_uri "${SOURCE_MONGO_URI}")" "${REQUIRED_DATABASE}" "${collection}"
  printf "  - backup %s: docker run ... mongodump --uri='%s' --db='%s' --collection='%s' --out=/work/destination-backup\n" \
    "${collection}" "$(mask_uri "${DESTINATION_MONGO_URI}")" "${REQUIRED_DATABASE}" "${collection}"
  printf "  - restore %s: docker run ... mongorestore --uri='%s' --nsInclude='%s.%s' --drop --noIndexRestore /work/source-dump/%s\n" \
    "${collection}" "$(mask_uri "${DESTINATION_MONGO_URI}")" "${REQUIRED_DATABASE}" "${collection}" "${REQUIRED_DATABASE}"
done)
EOF
}

dump_source_command() {
  [[ -n "${SOURCE_MONGO_URI}" ]] || die "--source-uri or SOURCE_MONGO_URI is required"

  prepare_run_dir
  write_manifest_header

  local source_db collection source_count
  source_db="$(extract_database_name "${SOURCE_MONGO_URI}")"
  require_supported_database "${source_db}"

  ensure_dir "${RUN_DIR}/source-dump"
  append_manifest "source_uri" "run" "$(mask_uri "${SOURCE_MONGO_URI}")"
  log_target_summary "Source" "${SOURCE_MONGO_URI}"

  for collection in "${SELECTED_COLLECTIONS[@]}"; do
    log "Dumping source collection '${collection}' into ${RUN_DIR}/source-dump"
    run_with_log "${RUN_DIR}/logs/dump-${collection}.log" \
      dump_collection "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${source_db}" "${collection}" "/work/source-dump"
    source_count="$(count_documents "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${source_db}" "${collection}")"
    append_manifest "source_count" "${collection}" "${source_count}"
    log "Source count for '${collection}': ${source_count}"
  done

  log "Source dump completed: ${RUN_DIR}/source-dump"
}

backup_destination_command() {
  [[ -n "${DESTINATION_MONGO_URI}" ]] || die "--destination-uri or DESTINATION_MONGO_URI is required"
  require_confirmation "$(extract_host_label "${DESTINATION_MONGO_URI}")"

  prepare_run_dir
  write_manifest_header

  local destination_db collection destination_before_count
  destination_db="$(extract_database_name "${DESTINATION_MONGO_URI}")"
  require_supported_database "${destination_db}"

  ensure_dir "${RUN_DIR}/destination-backup"
  append_manifest "destination_uri" "run" "$(mask_uri "${DESTINATION_MONGO_URI}")"

  log_target_summary "Destination backup target" "${DESTINATION_MONGO_URI}"
  for collection in "${SELECTED_COLLECTIONS[@]}"; do
    destination_before_count="$(count_documents "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}")"
    append_manifest "destination_before_count" "${collection}" "${destination_before_count}"
    log "Destination count before backup for '${collection}': ${destination_before_count}"
    run_with_log "${RUN_DIR}/logs/backup-${collection}.log" \
      dump_collection "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}" "/work/destination-backup"
  done

  log "Destination backup completed: ${RUN_DIR}/destination-backup"
}

restore_command() {
  [[ -n "${DESTINATION_MONGO_URI}" ]] || die "--destination-uri or DESTINATION_MONGO_URI is required"
  [[ -n "${RUN_DIR}" ]] || die "--run-dir is required for restore"
  RUN_DIR="$(absolute_path "${RUN_DIR}")"
  [[ -d "${RUN_DIR}" ]] || die "Run directory does not exist: ${RUN_DIR}"
  require_confirmation "$(extract_host_label "${DESTINATION_MONGO_URI}")"

  write_manifest_header

  local destination_db collection expected_count destination_after_count
  destination_db="$(extract_database_name "${DESTINATION_MONGO_URI}")"
  require_supported_database "${destination_db}"

  log_target_summary "Destination restore target" "${DESTINATION_MONGO_URI}"
  for collection in "${SELECTED_COLLECTIONS[@]}"; do
    [[ -f "$(required_dump_path "${collection}")" ]] || \
      die "Missing required dump for '${collection}': $(required_dump_path "${collection}")"
    expected_count="$(get_manifest_value "source_count" "${collection}")"
    run_with_log "${RUN_DIR}/logs/restore-${collection}.log" \
      restore_collection "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}"
    destination_after_count="$(count_documents "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}")"
    append_manifest "destination_after_count" "${collection}" "${destination_after_count}"
    log "Destination count after restore for '${collection}': ${destination_after_count}"
    [[ "${destination_after_count}" == "${expected_count}" ]] || \
      die "Count mismatch for '${collection}': expected ${expected_count}, got ${destination_after_count}"
  done

  log "Restore completed successfully"
}

main() {
  [[ -n "${SUBCOMMAND}" ]] || {
    usage
    exit 1
  }

  require_command docker
  require_command python3

  case "${SUBCOMMAND}" in
    plan|dump-source|backup-destination|restore)
      parse_args "$@"
      resolve_prompted_passwords
      resolve_selected_collections
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown subcommand: ${SUBCOMMAND}"
      ;;
  esac

  case "${SUBCOMMAND}" in
    plan)
      plan_command
      ;;
    dump-source)
      dump_source_command
      ;;
    backup-destination)
      backup_destination_command
      ;;
    restore)
      restore_command
      ;;
  esac
}

main "$@"
