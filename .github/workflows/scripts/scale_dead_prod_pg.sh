#!/usr/bin/env bash

set -euo pipefail

action="${ACTION:?ACTION is required}"
dead_color="${DEAD_COLOR:?DEAD_COLOR is required}"
networks="${NETWORKS:?NETWORKS is required}"

env_file="${ENV_FILE_DIR:-terraform/api/environments}/${dead_color}.yml"
timeout_seconds="${TIMEOUT_SECONDS:-3600}"

case "${networks}" in
  both) nets=(main test) ;;
  main|test) nets=("${networks}") ;;
  *)
    echo "Unsupported network selection ${networks}"
    exit 1
    ;;
esac

# The class terraform declares; `down` returns to it, so a later deploy plans no change.
declared_class() {
  local net="${1:?net is required}"
  local class

  class="$(yq eval ".enabled_nets.${net}.postgres.instance_class" "${env_file}")"
  if [[ -z "${class}" || "${class}" == "null" ]]; then
    echo "declared_class: .enabled_nets.${net}.postgres.instance_class is unset in ${env_file}" >&2
    exit 1
  fi
  echo "${class}"
}

target_class() {
  local net="${1:?net is required}"
  local requested

  if [[ "${action}" == "down" ]]; then
    declared_class "${net}"
    return
  fi

  case "${net}" in
    main) requested="${MAIN_INSTANCE_CLASS:?MAIN_INSTANCE_CLASS is required}" ;;
    test) requested="${TEST_INSTANCE_CLASS:?TEST_INSTANCE_CLASS is required}" ;;
  esac
  if [[ ! "${requested}" =~ ^db\.[a-z0-9]+\.[a-z0-9]+$ ]]; then
    echo "target_class: '${requested}' is not an RDS instance class" >&2
    exit 1
  fi
  echo "${requested}"
}

describe_instance() {
  local instance_id="${1:?instance_id is required}"

  aws rds describe-db-instances \
    --db-instance-identifier "${instance_id}" \
    --query 'DBInstances[0].{status:DBInstanceStatus,class:DBInstanceClass,pending:PendingModifiedValues.DBInstanceClass}' \
    --output json
}

# `aws rds wait db-instance-available` can return before the status leaves `available`.
wait_for_class() {
  local instance_id="${1:?instance_id is required}"
  local class="${2:?class is required}"
  local deadline=$((SECONDS + timeout_seconds))
  local state

  while ((SECONDS < deadline)); do
    state="$(describe_instance "${instance_id}")"
    if [[ "$(jq -r --arg c "${class}" '.status == "available" and .class == $c and .pending == null' <<<"${state}")" == "true" ]]; then
      echo "${instance_id} is available as ${class}."
      return 0
    fi
    echo "${instance_id}: $(jq -c . <<<"${state}")"
    sleep 30
  done

  echo "${instance_id} did not reach ${class} within ${timeout_seconds}s."
  exit 1
}

declare -A targets=()
declare -A previous=()

for net in "${nets[@]}"; do
  instance_id="${dead_color}-${net}-pg"
  class="$(target_class "${net}")"

  if ! state="$(describe_instance "${instance_id}" 2>/dev/null)"; then
    echo "${instance_id} does not exist; restore the dead colour first."
    exit 1
  fi
  current="$(jq -r '.class' <<<"${state}")"
  status="$(jq -r '.status' <<<"${state}")"
  previous["${instance_id}"]="${current}"

  if [[ "${current}" == "${class}" ]]; then
    echo "${instance_id} is already ${class}; skipping."
    continue
  fi
  if [[ "${status}" != "available" ]]; then
    echo "${instance_id} is ${status}, not available; refusing to modify it."
    exit 1
  fi

  echo "Modifying ${instance_id}: ${current} -> ${class}"
  aws rds modify-db-instance \
    --db-instance-identifier "${instance_id}" \
    --db-instance-class "${class}" \
    --apply-immediately \
    >/dev/null
  targets["${instance_id}"]="${class}"
done

for instance_id in "${!targets[@]}"; do
  wait_for_class "${instance_id}" "${targets[${instance_id}]}"
done

{
  echo "### Dead Prod Postgres Scale"
  echo "- Dead color: \`${dead_color}\`"
  echo "- Action: \`${action}\`"
  for net in "${nets[@]}"; do
    instance_id="${dead_color}-${net}-pg"
    echo "- \`${instance_id}\`: \`${previous[${instance_id}]}\` -> \`$(target_class "${net}")\`"
  done
  if [[ "${action}" == "up" ]]; then
    echo "- Note: the next deploy applies the class in \`${env_file}\`, which scales it back down."
  fi
} >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
