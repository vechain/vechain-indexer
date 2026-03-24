#!/usr/bin/env bash

set -euo pipefail

action="${ACTION:?ACTION is required}"
dead_color="${DEAD_COLOR:?DEAD_COLOR is required}"
ecs_cluster="${ECS_CLUSTER:?ECS_CLUSTER is required}"

services=(
  "${dead_color}-veworld-main-api-service"
  "${dead_color}-veworld-main-indexer-service"
  "${dead_color}-veworld-test-api-service"
  "${dead_color}-veworld-test-indexer-service"
)

cluster_exists() {
  local cluster_arn

  cluster_arn="$(aws ecs describe-clusters \
    --clusters "${ecs_cluster}" \
    --query 'clusters[0].clusterArn' \
    --output text 2>/dev/null || true)"

  [[ -n "${cluster_arn}" && "${cluster_arn}" != "None" ]]
}

describe_services() {
  aws ecs describe-services \
    --cluster "${ecs_cluster}" \
    --services "${services[@]}" \
    --query '{services: services[].{name:serviceName,desired:desiredCount,running:runningCount,pending:pendingCount,status:status}, failures: failures[].arn}' \
    --output json
}

count_missing_services() {
  local describe_json="${1:?describe_json is required}"

  printf '%s\n' "${describe_json}" | jq '.failures | length'
}

count_active_services() {
  local describe_json="${1:?describe_json is required}"

  printf '%s\n' "${describe_json}" | jq '[.services[] | select((.desired // 0) > 0 or (.running // 0) > 0)] | length'
}

count_not_fully_stopped_services() {
  local describe_json="${1:?describe_json is required}"

  printf '%s\n' "${describe_json}" | jq '[.services[] | select((.desired // 0) != 0 or (.running // 0) != 0)] | length'
}

count_not_running_services() {
  local describe_json="${1:?describe_json is required}"

  printf '%s\n' "${describe_json}" | jq '[.services[] | select((.desired // 0) < 1 or (.running // 0) < 1)] | length'
}

assert_no_missing_services() {
  local describe_json missing_count

  describe_json="$(describe_services)"
  missing_count="$(count_missing_services "${describe_json}")"
  if [[ "${missing_count}" != "0" ]]; then
    echo "One or more expected ECS services are missing from ${ecs_cluster}."
    printf '%s\n' "${describe_json}"
    exit 1
  fi
}

assert_stopped() {
  local describe_json active_count

  if ! cluster_exists; then
    echo "ECS cluster ${ecs_cluster} is missing; treating dead-prod services as already stopped."
    {
      echo "### Dead Prod Service State"
      echo "- ECS cluster: \`${ecs_cluster}\`"
      echo "- Status: cluster missing, treated as stopped"
      echo "- Note: the Atlas restore workflow only requires the target Atlas clusters; use the service workflow later to confirm ECS infrastructure exists before restart"
    } >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
    exit 0
  fi

  describe_json="$(describe_services)"
  if [[ "$(count_missing_services "${describe_json}")" != "0" ]]; then
    echo "One or more expected ECS services are missing from ${ecs_cluster}."
    printf '%s\n' "${describe_json}"
    exit 1
  fi

  active_count="$(count_active_services "${describe_json}")"

  {
    echo "### Dead Prod Service State"
    echo "- ECS cluster: \`${ecs_cluster}\`"
    echo "- Dead color: \`${dead_color}\`"
    echo "- Action: \`assert-stopped\`"
  } >> "${GITHUB_STEP_SUMMARY}"

  if [[ "${active_count}" != "0" ]]; then
    echo "Dead-prod services are still active; restore must not proceed."
    printf '%s\n' "${describe_json}"
    exit 1
  fi

  echo "All dead-prod services are already stopped."
}

update_services() {
  local desired_count="${1:?desired_count is required}"
  local waiter_target

  if ! cluster_exists; then
    echo "ECS cluster ${ecs_cluster} does not exist."
    exit 1
  fi

  assert_no_missing_services

  for service in "${services[@]}"; do
    echo "Setting ${service} desired count to ${desired_count}"
    aws ecs update-service \
      --cluster "${ecs_cluster}" \
      --service "${service}" \
      --desired-count "${desired_count}" \
      >/dev/null
  done

  echo "Waiting for ECS services to stabilise..."
  aws ecs wait services-stable --cluster "${ecs_cluster}" --services "${services[@]}"

  waiter_target="$(describe_services)"
  printf '%s\n' "${waiter_target}"

  if [[ "${desired_count}" == "0" ]]; then
    if [[ "$(count_not_fully_stopped_services "${waiter_target}")" != "0" ]]; then
      echo "One or more ECS services did not stop cleanly."
      exit 1
    fi
  else
    if [[ "$(count_not_running_services "${waiter_target}")" != "0" ]]; then
      echo "One or more ECS services did not start cleanly."
      exit 1
    fi
  fi

  {
    echo "### Dead Prod Service Action"
    echo "- ECS cluster: \`${ecs_cluster}\`"
    echo "- Dead color: \`${dead_color}\`"
    echo "- Action: \`${action}\`"
    echo "- Desired count applied: \`${desired_count}\`"
  } >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
}

case "${action}" in
  assert-stopped)
    assert_stopped
    ;;
  stop)
    update_services 0
    ;;
  start)
    update_services 1
    ;;
  *)
    echo "Unsupported action ${action}"
    exit 1
    ;;
esac
