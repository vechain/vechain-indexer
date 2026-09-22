#!/usr/bin/env bash

# Rebuilds the dead colour's Postgres instances from the live colour's snapshots.
# RDS cannot restore into an existing instance, so each one is replaced from the
# snapshot under the same identifier, which keeps the hostname the apps hold.

set -euo pipefail

source_color="${SOURCE_COLOR:?SOURCE_COLOR is required}"
target_color="${TARGET_COLOR:?TARGET_COLOR is required}"
terraform_dir="${TERRAFORM_DIR:-terraform/api}"
env_file_dir="${ENV_FILE_DIR:-terraform/api/environments}"
output_dir="${PG_RESTORE_OUTPUT_DIR:-restore-output}"

env_file="${env_file_dir}/${target_color}.yml"
[[ -f "${env_file}" ]] || { echo "Environment file ${env_file} not found."; exit 1; }
command -v yq >/dev/null 2>&1 || { echo "yq is required."; exit 1; }

mkdir -p "${output_dir}"

err_file="$(mktemp)"
trap 'rm -f "${err_file}"' EXIT

summary() {
  printf '%s\n' "$@" >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
}

aws_said() {
  grep -q "${1}" "${err_file}"
}

aws_failed() {
  echo "${1}"
  cat "${err_file}"
  exit 1
}

# Expired credentials or a throttle must not read as "there is nothing to restore from":
# that answer skips the restore and lets the colour deploy on stale data.
instance_exists() {
  aws rds describe-db-instances --db-instance-identifier "${1}" >/dev/null 2>"${err_file}" && return 0
  aws_said DBInstanceNotFound && return 1
  aws_failed "Could not look up ${1}."
}

# A snapshot names the instance it was taken from, and restoring one network from
# another's is silent and total, so the source is checked before anything is replaced.
validate_snapshot() {
  local net="${1}" snapshot="${2}" details source status engine
  details="$(aws rds describe-db-snapshots --db-snapshot-identifier "${snapshot}" --output json 2>"${err_file}")" \
    || aws_failed "Could not describe snapshot ${snapshot}."

  source="$(jq -r '.DBSnapshots[0].DBInstanceIdentifier // "an unknown instance"' <<<"${details}")"
  status="$(jq -r '.DBSnapshots[0].Status // "unknown"' <<<"${details}")"
  engine="$(jq -r '.DBSnapshots[0].Engine // "unknown"' <<<"${details}")"

  if [[ "${source}" != "${source_color}-${net}-pg" && "${source}" != "${target_color}-${net}-pg" ]]; then
    echo "Snapshot ${snapshot} was taken from ${source}, not ${source_color}-${net}-pg or ${target_color}-${net}-pg."
    exit 1
  fi
  if [[ "${status}" != "available" || "${engine}" != "postgres" ]]; then
    echo "Snapshot ${snapshot} is ${status} on engine ${engine}; it must be an available postgres snapshot."
    exit 1
  fi
}

newest_available_snapshot() {
  aws rds describe-db-snapshots \
    --db-instance-identifier "${1}" \
    --snapshot-type automated \
    --output json \
    | jq -r '
      [.DBSnapshots[] | select(.Status == "available")]
      | sort_by(.SnapshotCreateTime)
      | last
      | (.DBSnapshotIdentifier // empty)
    '
}

# RDS restores at the snapshot's size and cannot shrink, so the instance is sized to match.
snapshot_allocated_storage() {
  aws rds describe-db-snapshots \
    --db-snapshot-identifier "${1}" \
    --query 'DBSnapshots[0].AllocatedStorage' \
    --output text 2>"${err_file}" && return 0
  aws_failed "Could not read the size of snapshot ${1}."
}

net_map() {
  local -n entries="${1}"
  local json="{}" net
  for net in "${!entries[@]}"; do
    json="$(jq --arg k "${net}" --arg v "${entries[${net}]}" '.[$k] = $v' <<<"${json}")"
  done
  printf '%s\n' "${json}"
}

mapfile -t pg_nets < <(yq eval '.enabled_nets | to_entries | map(select(.value.postgres.enabled == true)) | .[].key' "${env_file}")
if [[ "${#pg_nets[@]}" -eq 0 ]]; then
  echo "No network in ${env_file} has Postgres enabled; nothing to restore."
  summary "### Dead Prod Postgres Restore" "- Status: no network has Postgres enabled"
  exit 0
fi

declare -A snapshots=()
declare -A storage_gb=()
skipped=()

for net in "${pg_nets[@]}"; do
  override_name="PG_SNAPSHOT_${net^^}"
  snapshot="${!override_name:-}"

  if [[ -n "${snapshot}" ]]; then
    validate_snapshot "${net}" "${snapshot}"
    echo "Using the snapshot supplied for ${net}: ${snapshot}"
  else
    live_instance="${source_color}-${net}-pg"
    if ! instance_exists "${live_instance}"; then
      echo "${live_instance} does not exist; leaving ${target_color}'s ${net} instance as it is."
      skipped+=("${net}")
      continue
    fi

    snapshot="$(newest_available_snapshot "${live_instance}")"
    if [[ -z "${snapshot}" ]]; then
      echo "${live_instance} has no available automated snapshot; aborting."
      exit 1
    fi
  fi

  snapshots["${net}"]="${snapshot}"
  storage_gb["${net}"]="$(snapshot_allocated_storage "${snapshot}")"
done

if [[ "${#snapshots[@]}" -eq 0 ]]; then
  summary "### Dead Prod Postgres Restore" \
    "- Status: skipped, \`${source_color}\` has no Postgres instance to restore from" \
    "- \`${target_color}\` keeps the data its own instances hold"
  exit 0
fi

restore_nets=("${!snapshots[@]}")
jq -n --argjson overrides "$(net_map snapshots)" --argjson storage "$(net_map storage_gb)" \
  '{pg_snapshot_override: $overrides, pg_allocated_storage_override: ($storage | map_values(tonumber))}' \
  > "${terraform_dir}/pg-snapshot.tfvars.json"
cat "${terraform_dir}/pg-snapshot.tfvars.json"

terraform -chdir="${terraform_dir}" init -backend-config="environments/${target_color}.config"
terraform -chdir="${terraform_dir}" workspace select "${target_color}"

plan_args=(-var-file=pg-snapshot.tfvars.json)
# -target takes a resource's dependencies, not its dependents: this hangs off the role, not the task.
plan_args+=(-target='aws_iam_role_policy_attachment.pg_prewarm_execution[0]')
for net in "${restore_nets[@]}"; do
  address="aws_db_instance.postgres[\"${net}\"]"
  plan_args+=("-target=${address}")
  # The run-task below needs it, and a colour never applied has neither.
  plan_args+=("-target=aws_ecs_task_definition.pg_prewarm[\"${net}\"]")
  # An instance already in state is replaced; one that is absent is simply created.
  if [[ -n "$(terraform -chdir="${terraform_dir}" state list "${address}" || true)" ]]; then
    plan_args+=("-replace=${address}")
  fi
done

terraform -chdir="${terraform_dir}" plan -out pg-restore.tfplan "${plan_args[@]}"
terraform -chdir="${terraform_dir}" apply pg-restore.tfplan

for net in "${restore_nets[@]}"; do
  aws rds wait db-instance-available --db-instance-identifier "${target_color}-${net}-pg"
  # The operator's record; no plan reads it, snapshot_identifier being ignored on update.
  aws ssm put-parameter --name "/veworld/${target_color}/pg-snapshot/${net}" \
    --type String --overwrite --value "${snapshots[${net}]}" >/dev/null
done

jq -n \
  --arg source_color "${source_color}" \
  --arg target_color "${target_color}" \
  --argjson snapshots "$(net_map snapshots)" \
  '{
    sourceColor: $source_color,
    targetColor: $target_color,
    generatedAt: now | todate,
    snapshots: $snapshots
  }' > "${output_dir}/pg-restore.json"

summary "### Dead Prod Postgres Restore" \
  "- Source color: \`${source_color}\`" \
  "- Target color: \`${target_color}\`"
for net in "${restore_nets[@]}"; do
  summary "- \`${target_color}-${net}-pg\` restored from \`${snapshots[${net}]}\` at ${storage_gb[${net}]} GiB"
done
for net in "${skipped[@]+"${skipped[@]}"}"; do
  summary "- \`${net}\`: skipped, \`${source_color}\` has no instance to restore from"
done
