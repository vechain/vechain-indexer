#!/usr/bin/env bash

# Starts the pg_prewarm task against each of the dead colour's Postgres instances. A restored
# instance lazy-loads its pages from S3 and runs on degraded I/O until each one has been read.

set -euo pipefail

target_color="${TARGET_COLOR:?TARGET_COLOR is required}"
ecs_cluster="${ECS_CLUSTER:?ECS_CLUSTER is required}"
env_file_dir="${ENV_FILE_DIR:-terraform/api/environments}"

env_file="${env_file_dir}/${target_color}.yml"
[[ -f "${env_file}" ]] || { echo "Environment file ${env_file} not found."; exit 1; }
command -v yq >/dev/null 2>&1 || { echo "yq is required."; exit 1; }

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

instance_exists() {
  aws rds describe-db-instances --db-instance-identifier "${1}" >/dev/null 2>"${err_file}" && return 0
  aws_said DBInstanceNotFound && return 1
  aws_failed "Could not look up ${1}."
}

task_definition_exists() {
  aws ecs describe-task-definition --task-definition "${1}" >/dev/null 2>"${err_file}" && return 0
  aws_said "Unable to describe task definition" && return 1
  aws_failed "Could not look up task definition ${1}."
}

# The restore workflow and the deploy both call this; whichever runs second finds the task.
already_warming() {
  aws ecs list-tasks --cluster "${ecs_cluster}" --family "${1}" --desired-status RUNNING \
    --query 'taskArns[0]' --output text 2>/dev/null | grep -qv '^None$'
}

# Where the indexer tasks run: the one network configuration that reaches Postgres.
indexer_network_configuration() {
  local service="${target_color}-veworld-${1}-indexer-service"
  # A destroyed service is still described, INACTIVE, with the subnets and security group it had.
  aws ecs describe-services \
    --cluster "${ecs_cluster}" \
    --services "${service}" \
    --query "services[?status=='ACTIVE'] | [0].networkConfiguration.awsvpcConfiguration" \
    --output json 2>"${err_file}" && return 0
  aws_said ClusterNotFound && { echo null; return 0; }
  aws_failed "Could not look up ${service}."
}

mapfile -t pg_nets < <(yq eval '.enabled_nets | to_entries | map(select(.value.postgres.enabled == true)) | .[].key' "${env_file}")

declare -A started_tasks=()
declare -A outcomes=()

for net in "${pg_nets[@]}"; do
  family="${target_color}-${net}-pg-prewarm"

  if ! instance_exists "${target_color}-${net}-pg"; then
    outcomes["${net}"]="skipped, no instance"
    continue
  fi
  if already_warming "${family}"; then
    outcomes["${net}"]="already running"
    continue
  fi
  if ! task_definition_exists "${family}"; then
    echo "::warning::${family} does not exist yet; the colour has not been applied. Skipping ${net}."
    outcomes["${net}"]="skipped, colour not applied"
    continue
  fi
  network="$(indexer_network_configuration "${net}")"
  if [[ "${network}" == "null" ]]; then
    echo "::warning::No active ${net} indexer service on ${ecs_cluster}; skipping ${net}."
    outcomes["${net}"]="skipped, no active indexer service"
    continue
  fi

  # run-task answers 200 with an empty task list when placement fails.
  started="$(aws ecs run-task \
    --cluster "${ecs_cluster}" \
    --task-definition "${family}" \
    --launch-type FARGATE \
    --started-by "pg-prewarm-${target_color}" \
    --network-configuration "awsvpcConfiguration=$(
      jq -r '"{subnets=[\(.subnets | join(","))],securityGroups=[\(.securityGroups | join(","))],assignPublicIp=\(.assignPublicIp // "DISABLED")}"' <<<"${network}"
    )" \
    --output json)"
  started_tasks["${net}"]="$(jq -r '.tasks[0].taskArn // empty' <<<"${started}")"
  if [[ -z "${started_tasks[${net}]}" ]]; then
    echo "Prewarm task for ${net} did not start: $(jq -c '.failures // []' <<<"${started}")"
    exit 1
  fi
  echo "Prewarming ${net}: ${started_tasks[${net}]}"
done

# Only the launch is waited on: a full pull takes hours, and the pages it has not
# reached yet cost the indexer nothing but a slower first read.
for net in "${!started_tasks[@]}"; do
  deadline=$((SECONDS + 600))
  while ((SECONDS < deadline)); do
    task="$(aws ecs describe-tasks --cluster "${ecs_cluster}" --tasks "${started_tasks[${net}]}" --query 'tasks[0]' --output json)"
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
  outcomes["${net}"]="started \`${started_tasks[${net}]}\`"
done

summary "### Dead Prod Postgres Prewarm" "- Colour: \`${target_color}\`"
for net in "${pg_nets[@]}"; do
  summary "- \`${target_color}-${net}-pg\`: ${outcomes[${net}]:-nothing to do}"
done
