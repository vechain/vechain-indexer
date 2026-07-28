#!/usr/bin/env bash

set -euo pipefail

action="${ACTION:?ACTION is required}"
dead_color="${DEAD_COLOR:?DEAD_COLOR is required}"
ecs_cluster="${ECS_CLUSTER:?ECS_CLUSTER is required}"

# Directory holding the Terraform env files (prod-blue.yml / prod-green.yml).
# These are the source of truth for each API service's autoscaling floor.
env_file_dir="${ENV_FILE_DIR:-terraform/api/environments}"

services=(
  "${dead_color}-veworld-main-api-service"
  "${dead_color}-veworld-main-indexer-service"
  "${dead_color}-veworld-test-api-service"
  "${dead_color}-veworld-test-indexer-service"
)

autoscaled_services=(
  "${dead_color}-veworld-main-api-service"
  "${dead_color}-veworld-test-api-service"
)

is_autoscaled() {
  local candidate="${1:?service is required}"
  local service
  for service in "${autoscaled_services[@]}"; do
    [[ "${service}" == "${candidate}" ]] && return 0
  done
  return 1
}

# Resolve the configured autoscaling minimum for an API service from the
# Terraform env file, so starting the dead color restores its real floor
# (e.g. mainnet API = 2) instead of a hardcoded 1. Falls back to 1 if the
# value cannot be resolved.
configured_min_capacity() {
  local service="${1:?service is required}"
  local net
  case "${service}" in
    *-main-api-service) net="main" ;;
    *-test-api-service) net="test" ;;
    *)
      echo "1"
      return 0
      ;;
  esac

  local env_file="${env_file_dir}/${dead_color}.yml"
  local min=""
  if [[ -f "${env_file}" ]] && command -v yq >/dev/null 2>&1; then
    min="$(yq eval ".enabled_nets.${net}.api.min_capacity" "${env_file}" 2>/dev/null || true)"
  fi

  if [[ -z "${min}" || "${min}" == "null" ]]; then
    echo "1"
  else
    echo "${min}"
  fi
}

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

scalable_target_resource_id() {
  local service="${1:?service is required}"

  printf 'service/%s/%s\n' "${ecs_cluster}" "${service}"
}

describe_scalable_target() {
  local service="${1:?service is required}"

  aws application-autoscaling describe-scalable-targets \
    --service-namespace ecs \
    --scalable-dimension ecs:service:DesiredCount \
    --resource-ids "$(scalable_target_resource_id "${service}")" \
    --query 'ScalableTargets[0]' \
    --output json
}

ensure_autoscaling_state() {
  local service="${1:?service is required}"
  local min_capacity="${2:?min_capacity is required}"
  local should_suspend="${3:?should_suspend is required}"
  local scalable_target_json
  local max_capacity
  local suspended_state

  scalable_target_json="$(describe_scalable_target "${service}")"
  if [[ "${scalable_target_json}" == "null" ]]; then
    echo "No scalable target configured for ${service}; skipping autoscaling state update."
    return 0
  fi

  max_capacity="$(printf '%s\n' "${scalable_target_json}" | jq -r '.MaxCapacity')"
  if [[ -z "${max_capacity}" || "${max_capacity}" == "null" ]]; then
    echo "Failed to resolve max capacity for ${service} scalable target."
    exit 1
  fi

  if [[ "${should_suspend}" == "true" ]]; then
    suspended_state='DynamicScalingInSuspended=true,DynamicScalingOutSuspended=true,ScheduledScalingSuspended=true'
  else
    suspended_state='DynamicScalingInSuspended=false,DynamicScalingOutSuspended=false,ScheduledScalingSuspended=false'
  fi

  echo "Updating autoscaling target for ${service}: min=${min_capacity}, max=${max_capacity}, suspended=${should_suspend}"
  aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --scalable-dimension ecs:service:DesiredCount \
    --resource-id "$(scalable_target_resource_id "${service}")" \
    --min-capacity "${min_capacity}" \
    --max-capacity "${max_capacity}" \
    --suspended-state "${suspended_state}" \
    >/dev/null
}

configure_autoscaling_for_stop() {
  local service

  for service in "${autoscaled_services[@]}"; do
    ensure_autoscaling_state "${service}" 0 true
  done
}

configure_autoscaling_for_start() {
  local service
  local min

  for service in "${autoscaled_services[@]}"; do
    min="$(configured_min_capacity "${service}")"
    ensure_autoscaling_state "${service}" "${min}" false
  done
}

count_autoscaling_targets_not_quiesced() {
  local not_quiesced=0
  local service
  local scalable_target_json
  local is_quiesced

  for service in "${autoscaled_services[@]}"; do
    scalable_target_json="$(describe_scalable_target "${service}")"
    if [[ "${scalable_target_json}" == "null" ]]; then
      continue
    fi

    is_quiesced="$(
      printf '%s\n' "${scalable_target_json}" | jq -r '
        (.MinCapacity == 0) and
        (.SuspendedState.DynamicScalingInSuspended == true) and
        (.SuspendedState.DynamicScalingOutSuspended == true) and
        (.SuspendedState.ScheduledScalingSuspended == true)
      '
    )"

    if [[ "${is_quiesced}" != "true" ]]; then
      not_quiesced=$((not_quiesced + 1))
    fi
  done

  printf '%s\n' "${not_quiesced}"
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
  local autoscaling_not_quiesced_count
  local missing_count

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
  missing_count="$(count_missing_services "${describe_json}")"
  if [[ "${missing_count}" == "${#services[@]}" ]]; then
    echo "All expected ECS services are missing from ${ecs_cluster}; treating dead-prod services as already stopped."
    {
      echo "### Dead Prod Service State"
      echo "- ECS cluster: \`${ecs_cluster}\`"
      echo "- Dead color: \`${dead_color}\`"
      echo "- Action: \`assert-stopped\`"
      echo "- Status: all expected ECS services missing, treated as stopped"
      echo "- Note: this is expected if the dead environment was previously destroyed"
    } >> "${GITHUB_STEP_SUMMARY}"
    exit 0
  fi

  if [[ "${missing_count}" != "0" ]]; then
    echo "Some expected ECS services are missing from ${ecs_cluster}; refusing to treat the dead environment as stopped."
    printf '%s\n' "${describe_json}"
    exit 1
  fi

  active_count="$(count_active_services "${describe_json}")"
  autoscaling_not_quiesced_count="$(count_autoscaling_targets_not_quiesced)"

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

  if [[ "${autoscaling_not_quiesced_count}" != "0" ]]; then
    echo "Dead-prod API service autoscaling is still enabled; restore must not proceed."
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

  if [[ "${desired_count}" == "0" ]]; then
    configure_autoscaling_for_stop
  else
    configure_autoscaling_for_start
  fi

  for service in "${services[@]}"; do
    local service_desired="${desired_count}"
    # When starting, bring autoscaled API services up to their configured floor
    # so we never start the dead color below its autoscaling minimum. Stopping
    # (desired_count=0) applies uniformly to every service.
    if [[ "${desired_count}" != "0" ]] && is_autoscaled "${service}"; then
      service_desired="$(configured_min_capacity "${service}")"
    fi

    echo "Setting ${service} desired count to ${service_desired}"
    aws ecs update-service \
      --cluster "${ecs_cluster}" \
      --service "${service}" \
      --desired-count "${service_desired}" \
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
