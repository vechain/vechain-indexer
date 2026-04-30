#!/usr/bin/env bash
set -euo pipefail

# Validate env-var-driven integer settings whose bad values would either hang
# (PARALLEL_SLICES=0) or silently misbehave (CHUNK_THRESHOLD, CHUNK_WIDTH non-numeric).
# Called after assignment so `exit 1` runs in the main shell, not a subshell.
require_positive_int() {
  local name="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "Error: ${name} must be a positive integer, got '${value}'" >&2
    exit 1
  fi
}

readonly SCRIPT_NAME="$(basename "$0")"
readonly SCRIPT_PATH="$0"
readonly SCRIPT_DIR_LOCAL_DUMP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DEFAULT_RUN_ROOT="${SCRIPT_DIR_LOCAL_DUMP}/runs"
readonly REQUIRED_DATABASE="vechain"
readonly MANIFEST_NAME="manifest.txt"
readonly RESTORE_WORKERS="${MONGO_RESTORE_WORKERS:-4}"
readonly CHUNK_WORKERS="${MONGO_CHUNK_WORKERS:-16}"
readonly CHUNK_THRESHOLD="${MONGO_CHUNK_THRESHOLD:-5000000}"
readonly CHUNK_FIELD="${MONGO_CHUNK_FIELD:-blockNumber}"
readonly CHUNK_WIDTH="${MONGO_CHUNK_WIDTH:-10000}"
readonly PARALLEL_SLICES="${MONGO_PARALLEL_SLICES:-1}"
readonly MONGO_IMAGE="${MONGO_IMAGE:-mongo:8.0}"
readonly RUN_ID="$$"

require_positive_int "MONGO_CHUNK_THRESHOLD" "${CHUNK_THRESHOLD}"
require_positive_int "MONGO_CHUNK_WIDTH" "${CHUNK_WIDTH}"
require_positive_int "MONGO_PARALLEL_SLICES" "${PARALLEL_SLICES}"

# Track in-flight slice worker PIDs so an unexpected exit kills them rather
# than leaving orphan docker / mongorestore containers running against Atlas.
SLICE_WORKER_PIDS=()
LAST_FINISHED_SLICE_PID=""
cleanup_slice_workers() {
  if (( ${#SLICE_WORKER_PIDS[@]} > 0 )); then
    local pid
    for pid in "${SLICE_WORKER_PIDS[@]}"; do
      kill "${pid}" 2>/dev/null || true
    done
    wait 2>/dev/null || true
    SLICE_WORKER_PIDS=()
  fi
}
trap cleanup_slice_workers EXIT

# Block until any worker in SLICE_WORKER_PIDS finishes. Removes that pid from
# the array, stores it in LAST_FINISHED_SLICE_PID, and returns its exit status.
# Polls via `kill -0` because `wait -n` is bash 4.3+ (macOS default is 3.2).
wait_any_slice_worker() {
  local pid found_pid="" rc new_pids=() p
  while [[ -z "${found_pid}" ]]; do
    for pid in "${SLICE_WORKER_PIDS[@]}"; do
      if ! kill -0 "${pid}" 2>/dev/null; then
        found_pid="${pid}"
        break
      fi
    done
    [[ -z "${found_pid}" ]] && sleep 0.2
  done
  wait "${found_pid}"
  rc=$?
  for p in "${SLICE_WORKER_PIDS[@]}"; do
    [[ "${p}" != "${found_pid}" ]] && new_pids+=("${p}")
  done
  SLICE_WORKER_PIDS=("${new_pids[@]}")
  LAST_FINISHED_SLICE_PID="${found_pid}"
  return ${rc}
}

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
  ${SCRIPT_NAME} stream-restore --source-uri URI --destination-uri URI --collections name1,name2 [--run-dir DIR] [--prompt-source-password] [--prompt-destination-password] [--no-index-restore] --yes --confirm-target EXPECTED_DESTINATION_HOST

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

# In-place progress bar on stderr. Pass the current step, total steps, and a label.
chunk_progress() {
  local current="$1"
  local total="$2"
  local label="$3"
  local width=40
  local pct=$(( total > 0 ? current * 100 / total : 0 ))
  local filled=$(( total > 0 ? current * width / total : 0 ))
  local bar="" i
  for (( i = 0; i < filled; i++ )); do bar+='#'; done
  for (( i = filled; i < width; i++ )); do bar+='-'; done
  printf '\r  %-30s [%s] %d/%d (%d%%)   ' "${label}" "${bar}" "${current}" "${total}" "${pct}" >&2
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

# Append query params for long-running write operations against Atlas.
# Disables the per-op socket timeout and trims idle pool connections so flaky
# shard connections are recycled before the operation depends on them.
harden_uri_for_long_writes() {
  local uri="$1"
  local extra="socketTimeoutMS=0&maxIdleTimeMS=120000"
  if [[ "${uri}" == *"?"* ]]; then
    printf '%s&%s' "${uri}" "${extra}"
  else
    printf '%s?%s' "${uri}" "${extra}"
  fi
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
    '$1 == wanted_key && $2 == wanted_collection { value = $3; found = 1 } END { if (!found) exit 1; print value }' \
    "${manifest}" || die "Manifest entry not found for ${key}/${collection}"
}

manifest_has_value() {
  local key="$1"
  local collection="$2"
  local manifest
  manifest="$(manifest_path)"
  [[ -f "${manifest}" ]] || return 1
  awk -F'|' -v wanted_key="${key}" -v wanted_collection="${collection}" \
    '$1 == wanted_key && $2 == wanted_collection { found = 1 } END { exit !found }' \
    "${manifest}"
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
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -v "${mounted_run_dir}:/work" \
    -w /work \
    "${MONGO_IMAGE}" \
    "$@"
}

count_documents() {
  local mounted_run_dir="$1"
  local uri="$2"
  local db_name="$3"
  local collection="$4"
  local normalized_uri raw count
  normalized_uri="$(normalize_uri_for_docker "${uri}")"

  raw="$(docker_mongo "${mounted_run_dir}" \
    mongosh "${normalized_uri}" --quiet \
    --eval "const database = db.getSiblingDB('${db_name}'); print(database.getCollection('${collection}').estimatedDocumentCount());" \
    | tr -d '[:space:]')"
  count="$(printf '%s' "${raw}" | grep -oE '[0-9]+$' || true)"
  [[ -n "${count}" ]] || die "Could not parse document count for '${collection}' (raw output: ${raw})"
  printf '%s' "${count}"
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
  normalized_uri="$(harden_uri_for_long_writes "$(normalize_uri_for_docker "${uri}")")"
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
    --numInsertionWorkersPerCollection ${RESTORE_WORKERS} \
    ${index_flag[@]+"${index_flag[@]}"} \
    "${restore_file}"
}

required_dump_path() {
  local collection="$1"
  printf '%s/source-dump/%s/%s.bson' "${RUN_DIR}" "${REQUIRED_DATABASE}" "${collection}"
}

stream_collection() {
  local mounted_run_dir="$1"
  local source_uri="$2"
  local destination_uri="$3"
  local db_name="$4"
  local collection="$5"
  local source_normalized destination_normalized index_flag

  source_normalized="$(normalize_uri_for_docker "${source_uri}")"
  destination_normalized="$(harden_uri_for_long_writes "$(normalize_uri_for_docker "${destination_uri}")")"

  index_flag=""
  if [[ "${NO_INDEX_RESTORE}" -eq 1 ]]; then
    index_flag="--noIndexRestore"
  fi

  docker run --rm \
    --add-host=host.docker.internal:host-gateway \
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -v "${mounted_run_dir}:/work" \
    -w /work \
    -e SRC_URI="${source_normalized}" \
    -e DST_URI="${destination_normalized}" \
    "${MONGO_IMAGE}" \
    bash -c "set -euo pipefail; mongodump --uri=\"\$SRC_URI\" --db='${db_name}' --collection='${collection}' --archive | mongorestore --uri=\"\$DST_URI\" --archive --nsInclude='${db_name}.${collection}' --drop --numInsertionWorkersPerCollection ${RESTORE_WORKERS} ${index_flag}"
}

drop_destination_collection() {
  local mounted_run_dir="$1"
  local destination_uri="$2"
  local db_name="$3"
  local collection="$4"
  local normalized_uri
  normalized_uri="$(normalize_uri_for_docker "${destination_uri}")"

  docker_mongo "${mounted_run_dir}" \
    mongosh "${normalized_uri}" --quiet \
    --eval "db.getSiblingDB('${db_name}').getCollection('${collection}').drop();" \
    >/dev/null
}

compute_slice_boundaries() {
  local mounted_run_dir="$1"
  local source_uri="$2"
  local db_name="$3"
  local collection="$4"
  local chunk_field="$5"
  local chunk_width="$6"
  local normalized_uri raw
  normalized_uri="$(normalize_uri_for_docker "${source_uri}")"

  # Read min/max of the chunk field via two indexed queries (cheap when
  # ${chunk_field} is indexed) and partition into fixed-width slices.
  # Variance in per-slice doc count is fine — we just need bounded slice size.
  raw="$(docker_mongo "${mounted_run_dir}" \
    mongosh "${normalized_uri}" --quiet \
    --eval "const c = db.getSiblingDB('${db_name}').getCollection('${collection}');
            const proj = {${chunk_field}: 1, _id: 0};
            const filter = {${chunk_field}: {\$exists: true}};
            const mn = c.find(filter, proj).sort({${chunk_field}: 1}).limit(1).toArray();
            const mx = c.find(filter, proj).sort({${chunk_field}: -1}).limit(1).toArray();
            if (mn.length === 0) { print('[]'); quit(); }
            // BSON Long needs explicit Number() coercion — its valueOf() is unreliable across mongosh versions.
            const lo = Number(mn[0].${chunk_field});
            const hi = Number(mx[0].${chunk_field});
            const boundaries = [];
            for (let v = lo + ${chunk_width}; v <= hi; v += ${chunk_width}) {
              boundaries.push(v);
            }
            print(EJSON.stringify(boundaries));" \
    | tr -d '\r' | grep -E '^\[' | tail -1)"
  [[ -n "${raw}" ]] || die "Failed to compute slice boundaries for '${collection}' on field '${chunk_field}'"
  printf '%s' "${raw}"
}

slice_query_for() {
  local boundaries_json="$1"
  local slice_index="$2"
  local total_slices="$3"
  local chunk_field="$4"

  BOUNDARIES="${boundaries_json}" K="${slice_index}" TOTAL="${total_slices}" FIELD="${chunk_field}" \
    python3 -c '
import json, os
boundaries = json.loads(os.environ["BOUNDARIES"])
k = int(os.environ["K"])
total = int(os.environ["TOTAL"])
field = os.environ["FIELD"]
clauses = {}
if k > 0:
    clauses["$gte"] = boundaries[k - 1]
if k < total - 1:
    clauses["$lt"] = boundaries[k]
# When neither bound applies (single-slice covers everything) emit a match-all
# query rather than {field: {}} which matches nothing.
if not clauses:
    print("{}")
else:
    print(json.dumps({field: clauses}, separators=(",", ":")))
'
}

delete_range_on_destination() {
  local mounted_run_dir="$1"
  local destination_uri="$2"
  local db_name="$3"
  local collection="$4"
  local query_json="$5"
  local slice_index="$6"
  local normalized_uri
  normalized_uri="$(harden_uri_for_long_writes "$(normalize_uri_for_docker "${destination_uri}")")"

  docker run --rm \
    --name "restore-${collection}-prune-${slice_index}-${RUN_ID}" \
    --add-host=host.docker.internal:host-gateway \
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -v "${mounted_run_dir}:/work" \
    -w /work \
    -e QUERY_JSON="${query_json}" \
    "${MONGO_IMAGE}" \
    mongosh "${normalized_uri}" --quiet \
    --eval "const c = db.getSiblingDB('${db_name}').getCollection('${collection}');
            const q = EJSON.parse(process.env.QUERY_JSON);
            const r = c.deleteMany(q);
            print(r.deletedCount);" \
    >/dev/null
}

stream_slice() {
  local mounted_run_dir="$1"
  local source_uri="$2"
  local destination_uri="$3"
  local db_name="$4"
  local collection="$5"
  local query_json="$6"
  local slice_index="$7"
  local source_normalized destination_normalized

  source_normalized="$(normalize_uri_for_docker "${source_uri}")"
  destination_normalized="$(harden_uri_for_long_writes "$(normalize_uri_for_docker "${destination_uri}")")"

  # Slice mongorestore always skips index restoration — indexes are built once
  # at the end of the chunked run via restore_indexes_at_end. writeConcern w:1
  # skips majority-ack overhead during the load; the count verification at the
  # end of stream_restore_command is the integrity gate.
  docker run --rm \
    --name "restore-${collection}-slice-${slice_index}-${RUN_ID}" \
    --add-host=host.docker.internal:host-gateway \
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -v "${mounted_run_dir}:/work" \
    -w /work \
    -e SRC_URI="${source_normalized}" \
    -e DST_URI="${destination_normalized}" \
    -e SLICE_QUERY="${query_json}" \
    "${MONGO_IMAGE}" \
    bash -c "set -euo pipefail; mongodump --uri=\"\$SRC_URI\" --db='${db_name}' --collection='${collection}' --query=\"\$SLICE_QUERY\" --archive | mongorestore --uri=\"\$DST_URI\" --archive --nsInclude='${db_name}.${collection}' --numInsertionWorkersPerCollection ${CHUNK_WORKERS} --writeConcern='{w:1}' --noIndexRestore"
}

restore_indexes_at_end() {
  local mounted_run_dir="$1"
  local source_uri="$2"
  local destination_uri="$3"
  local db_name="$4"
  local collection="$5"
  local source_normalized destination_normalized indexes_json
  source_normalized="$(normalize_uri_for_docker "${source_uri}")"
  destination_normalized="$(harden_uri_for_long_writes "$(normalize_uri_for_docker "${destination_uri}")")"

  indexes_json="$(docker_mongo "${mounted_run_dir}" \
    mongosh "${source_normalized}" --quiet \
    --eval "const idx = db.getSiblingDB('${db_name}').getCollection('${collection}').getIndexes()
              .filter(i => i.name !== '_id_')
              .map(({ns, v, ...rest}) => rest);
            print(EJSON.stringify(idx));" \
    | tr -d '\r' | grep -E '^\[' | tail -1)"

  if [[ -z "${indexes_json}" ]] || [[ "${indexes_json}" == "[]" ]]; then
    log "No secondary indexes to restore for '${collection}'"
    return 0
  fi

  log "Restoring secondary indexes for '${collection}' on destination"
  docker run --rm \
    --name "restore-${collection}-indexes-${RUN_ID}" \
    --add-host=host.docker.internal:host-gateway \
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -v "${mounted_run_dir}:/work" \
    -w /work \
    -e INDEXES_JSON="${indexes_json}" \
    "${MONGO_IMAGE}" \
    mongosh "${destination_normalized}" --quiet \
    --eval "const idx = EJSON.parse(process.env.INDEXES_JSON);
            print('Creating ' + idx.length + ' indexes for ${collection}');
            const r = db.getSiblingDB('${db_name}').runCommand({createIndexes: '${collection}', indexes: idx});
            if (!r.ok) { print('createIndexes failed: ' + EJSON.stringify(r)); quit(1); }"
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

stream_restore_command() {
  [[ -n "${SOURCE_MONGO_URI}" ]] || die "--source-uri or SOURCE_MONGO_URI is required"
  [[ -n "${DESTINATION_MONGO_URI}" ]] || die "--destination-uri or DESTINATION_MONGO_URI is required"
  require_confirmation "$(extract_host_label "${DESTINATION_MONGO_URI}")"

  prepare_run_dir
  write_manifest_header

  local source_db destination_db collection
  local source_count destination_after_count
  source_db="$(extract_database_name "${SOURCE_MONGO_URI}")"
  destination_db="$(extract_database_name "${DESTINATION_MONGO_URI}")"
  require_supported_database "${source_db}"
  require_supported_database "${destination_db}"

  append_manifest "source_uri" "run" "$(mask_uri "${SOURCE_MONGO_URI}")"
  append_manifest "destination_uri" "run" "$(mask_uri "${DESTINATION_MONGO_URI}")"

  log_target_summary "Source" "${SOURCE_MONGO_URI}"
  log_target_summary "Destination stream target" "${DESTINATION_MONGO_URI}"

  for collection in "${SELECTED_COLLECTIONS[@]}"; do
    source_count="$(count_documents "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${source_db}" "${collection}")"
    append_manifest "source_count" "${collection}" "${source_count}"
    log "Source count for '${collection}': ${source_count}"

    if (( source_count > CHUNK_THRESHOLD )); then
      log "Collection '${collection}' (${source_count} docs) exceeds chunk threshold (${CHUNK_THRESHOLD}); using chunked path"
      chunked_stream_collection "${source_count}" "${source_db}" "${destination_db}" "${collection}"
    else
      log "Streaming '${collection}' from source to destination"
      run_with_log "${RUN_DIR}/logs/stream-${collection}.log" \
        stream_collection "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${DESTINATION_MONGO_URI}" "${source_db}" "${collection}"
    fi

    destination_after_count="$(count_documents "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}")"
    append_manifest "destination_after_count" "${collection}" "${destination_after_count}"
    log "Destination count after stream for '${collection}': ${destination_after_count}"
    [[ "${destination_after_count}" == "${source_count}" ]] || \
      die "Count mismatch for '${collection}': expected ${source_count}, got ${destination_after_count}"
  done

  log "Stream restore completed successfully"
}

process_one_slice() {
  local k="$1"
  local collection="$2"
  local source_db="$3"
  local destination_db="$4"
  local query_json="$5"
  local slice_log="$6"

  if ! { delete_range_on_destination "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}" "${query_json}" "${k}"; } >>"${slice_log}" 2>&1; then
    echo "Slice ${k} delete-range failed; see ${slice_log}" >&2
    return 1
  fi

  if ! { stream_slice "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${DESTINATION_MONGO_URI}" "${source_db}" "${collection}" "${query_json}" "${k}"; } >>"${slice_log}" 2>&1; then
    echo "Slice ${k} stream failed; see ${slice_log}" >&2
    return 1
  fi

  append_manifest "slice_${k}_done" "${collection}" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
}

chunked_stream_collection() {
  local source_count="$1"
  local source_db="$2"
  local destination_db="$3"
  local collection="$4"

  local slice_count boundaries_json query_json k

  if manifest_has_value "slice_boundaries" "${collection}"; then
    boundaries_json="$(get_manifest_value "slice_boundaries" "${collection}")"
    log "Resuming chunked transfer for '${collection}' with existing boundaries"
  else
    log "Computing slice boundaries for '${collection}' on field '${CHUNK_FIELD}' (width ${CHUNK_WIDTH})"
    boundaries_json="$(compute_slice_boundaries "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${source_db}" "${collection}" "${CHUNK_FIELD}" "${CHUNK_WIDTH}")"
    append_manifest "slice_boundaries" "${collection}" "${boundaries_json}"
  fi

  slice_count="$(printf '%s' "${boundaries_json}" | python3 -c 'import json, sys; print(len(json.loads(sys.stdin.read())) + 1)')"
  log "Slice plan for '${collection}': ${slice_count} slices on '${CHUNK_FIELD}'"

  if (( slice_count < 2 )); then
    log "Only one slice computed for '${collection}'; falling back to simple stream (collection range narrower than CHUNK_WIDTH=${CHUNK_WIDTH})"
    run_with_log "${RUN_DIR}/logs/stream-${collection}.log" \
      stream_collection "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${DESTINATION_MONGO_URI}" "${source_db}" "${collection}"
    return 0
  fi

  if ! manifest_has_value "drop_done" "${collection}"; then
    log "Dropping destination collection '${collection}'"
    drop_destination_collection "${RUN_DIR}" "${DESTINATION_MONGO_URI}" "${destination_db}" "${collection}"
    append_manifest "drop_done" "${collection}" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  fi

  local completed=0 query_json slice_log
  chunk_progress 0 "${slice_count}" "${collection}"
  for (( k = 0; k < slice_count; k++ )); do
    if manifest_has_value "slice_${k}_done" "${collection}"; then
      completed=$(( completed + 1 ))
      chunk_progress "${completed}" "${slice_count}" "${collection}"
      continue
    fi

    # Pool full — wait for any worker to finish before launching a new one.
    while (( ${#SLICE_WORKER_PIDS[@]} >= PARALLEL_SLICES )); do
      if ! wait_any_slice_worker; then
        printf '\n' >&2
        die "Slice failed (worker pid ${LAST_FINISHED_SLICE_PID}); inspect ${RUN_DIR}/logs/ for the matching slice log"
      fi
      completed=$(( completed + 1 ))
      chunk_progress "${completed}" "${slice_count}" "${collection}"
    done

    query_json="$(slice_query_for "${boundaries_json}" "${k}" "${slice_count}" "${CHUNK_FIELD}")"
    slice_log="${RUN_DIR}/logs/stream-${collection}-slice-${k}.log"

    process_one_slice "${k}" "${collection}" "${source_db}" "${destination_db}" "${query_json}" "${slice_log}" &
    SLICE_WORKER_PIDS+=($!)
  done

  while (( ${#SLICE_WORKER_PIDS[@]} > 0 )); do
    if ! wait_any_slice_worker; then
      printf '\n' >&2
      die "Slice failed (worker pid ${LAST_FINISHED_SLICE_PID}); inspect ${RUN_DIR}/logs/ for the matching slice log"
    fi
    completed=$(( completed + 1 ))
    chunk_progress "${completed}" "${slice_count}" "${collection}"
  done
  printf '\n' >&2

  if [[ "${NO_INDEX_RESTORE}" -eq 1 ]]; then
    log "Skipping index restoration for '${collection}' (--no-index-restore)"
  elif manifest_has_value "indexes_done" "${collection}"; then
    log "Indexes already restored for '${collection}'; skipping"
  else
    restore_indexes_at_end "${RUN_DIR}" "${SOURCE_MONGO_URI}" "${DESTINATION_MONGO_URI}" "${source_db}" "${collection}"
    append_manifest "indexes_done" "${collection}" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  fi

  log "Chunked stream completed for '${collection}'"
}

main() {
  [[ -n "${SUBCOMMAND}" ]] || {
    usage
    exit 1
  }

  require_command docker
  require_command python3

  case "${SUBCOMMAND}" in
    plan|dump-source|backup-destination|restore|stream-restore)
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
    stream-restore)
      stream_restore_command
      ;;
  esac
}

main "$@"
