#!/usr/bin/env bash

# Rebuilds the dead colour's Postgres instances from the live colour's snapshots.
# RDS cannot restore into an existing instance, so each one is replaced from the
# snapshot under the same identifier, which keeps the hostname the apps hold.

set -euo pipefail

source_color="${SOURCE_COLOR:?SOURCE_COLOR is required}"
target_color="${TARGET_COLOR:?TARGET_COLOR is required}"
ecs_cluster="${ECS_CLUSTER:?ECS_CLUSTER is required}"
terraform_dir="${TERRAFORM_DIR:-terraform/api}"
env_file_dir="${ENV_FILE_DIR:-terraform/api/environments}"
output_dir="${PG_RESTORE_OUTPUT_DIR:-restore-output}"

env_file="${env_file_dir}/${target_color}.yml"
[[ -f "${env_file}" ]] || { echo "Environment file ${env_file} not found."; exit 1; }
command -v yq >/dev/null 2>&1 || { echo "yq is required."; exit 1; }

mkdir -p "${output_dir}"

summary() {
  printf '%s\n' "$@" >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
}

instance_exists() {
  aws rds describe-db-instances --db-instance-identifier "${1}" >/dev/null 2>&1
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

# Where the indexer tasks run: the one network configuration that reaches Postgres.
indexer_network_configuration() {
  aws ecs describe-services \
    --cluster "${ecs_cluster}" \
    --services "${target_color}-veworld-${1}-indexer-service" \
    --query 'services[0].networkConfiguration.awsvpcConfiguration' \
    --output json 2>/dev/null || echo null
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
skipped=()

for net in "${pg_nets[@]}"; do
  override_name="PG_SNAPSHOT_${net^^}"
  snapshot="${!override_name:-}"

  if [[ -n "${snapshot}" ]]; then
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
done

if [[ "${#snapshots[@]}" -eq 0 ]]; then
  summary "### Dead Prod Postgres Restore" \
    "- Status: skipped, \`${source_color}\` has no Postgres instance to restore from" \
    "- \`${target_color}\` keeps the data its own instances hold"
  exit 0
fi

restore_nets=("${!snapshots[@]}")
jq -n --argjson overrides "$(net_map snapshots)" '{pg_snapshot_override: $overrides}' \
  > "${terraform_dir}/pg-snapshot.tfvars.json"
cat "${terraform_dir}/pg-snapshot.tfvars.json"

terraform -chdir="${terraform_dir}" init -backend-config="environments/${target_color}.config"
terraform -chdir="${terraform_dir}" workspace select "${target_color}"

plan_args=(-var-file=pg-snapshot.tfvars.json)
for net in "${restore_nets[@]}"; do
  address="aws_db_instance.postgres[\"${net}\"]"
  plan_args+=("-target=${address}")
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

# A restored instance lazy-loads its pages from S3 and runs on degraded I/O until each
# one has been read. The task pulls them all; the indexer may catch up while it does.
declare -A prewarm_tasks=()
for net in "${restore_nets[@]}"; do
  network="$(indexer_network_configuration "${net}")"
  if [[ "${network}" == "null" ]]; then
    echo "::warning::No ${net} indexer service on ${ecs_cluster}; prewarm not started for ${net}."
    continue
  fi

  prewarm_tasks["${net}"]="$(aws ecs run-task \
    --cluster "${ecs_cluster}" \
    --task-definition "${target_color}-${net}-pg-prewarm" \
    --launch-type FARGATE \
    --started-by "pg-restore-${target_color}" \
    --network-configuration "awsvpcConfiguration=$(
      jq -r '"{subnets=[\(.subnets | join(","))],securityGroups=[\(.securityGroups | join(","))],assignPublicIp=\(.assignPublicIp // "DISABLED")}"' <<<"${network}"
    )" \
    --query 'tasks[0].taskArn' --output text)"
  echo "Prewarming ${net}: ${prewarm_tasks[${net}]}"
done

# Only the launch is waited on: a full pull takes hours, and the pages it has not
# reached yet cost the indexer nothing but a slower first read.
for net in "${!prewarm_tasks[@]}"; do
  deadline=$((SECONDS + 600))
  while ((SECONDS < deadline)); do
    task="$(aws ecs describe-tasks --cluster "${ecs_cluster}" --tasks "${prewarm_tasks[${net}]}" --query 'tasks[0]' --output json)"
    status="$(jq -r '.lastStatus // "PENDING"' <<<"${task}")"
    if [[ "${status}" == "RUNNING" ]]; then break; fi
    if [[ "${status}" == "STOPPED" ]]; then
      if [[ "$(jq -r '.containers[0].exitCode // 1' <<<"${task}")" != "0" ]]; then
        echo "Prewarm task for ${net} failed: $(jq -r '.stoppedReason // "unknown"' <<<"${task}")"
        exit 1
      fi
      break
    fi
    sleep 10
  done
done

jq -n \
  --arg source_color "${source_color}" \
  --arg target_color "${target_color}" \
  --argjson snapshots "$(net_map snapshots)" \
  --argjson prewarm "$(net_map prewarm_tasks)" \
  '{
    sourceColor: $source_color,
    targetColor: $target_color,
    generatedAt: now | todate,
    snapshots: $snapshots,
    prewarmTasks: $prewarm
  }' > "${output_dir}/pg-restore.json"

summary "### Dead Prod Postgres Restore" \
  "- Source color: \`${source_color}\`" \
  "- Target color: \`${target_color}\`"
for net in "${restore_nets[@]}"; do
  summary "- \`${target_color}-${net}-pg\` restored from \`${snapshots[${net}]}\`, prewarm task \`${prewarm_tasks[${net}]:-not started}\`"
done
for net in "${skipped[@]+"${skipped[@]}"}"; do
  summary "- \`${net}\`: skipped, \`${source_color}\` has no instance to restore from"
done
