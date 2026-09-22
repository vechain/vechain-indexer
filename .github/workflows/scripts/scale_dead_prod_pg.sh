#!/usr/bin/env bash

set -euo pipefail

action="${ACTION:?ACTION is required}"
dead_color="${DEAD_COLOR:?DEAD_COLOR is required}"
networks="${NETWORKS:?NETWORKS is required}"

env_file="${ENV_FILE_DIR:-terraform/api/environments}/${dead_color}.yml"
timeout_seconds="${TIMEOUT_SECONDS:-3600}"

case "${action}" in
  up|down|assert-declared) ;;
  *)
    echo "Unsupported action ${action}"
    exit 1
    ;;
esac

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

# Runs an aws call, printing nothing when it fails with only the not-found code given; any
# other failure (credentials, access, throttling) must abort rather than read as "absent".
aws_or_missing() {
  local not_found="${1:?not_found is required}"
  local out err

  shift
  err="$(mktemp)"
  if out="$("$@" 2>"${err}")"; then
    rm -f "${err}"
    [[ -z "${out}" ]] || printf '%s\n' "${out}"
    return 0
  fi
  if grep -q "(${not_found})" "${err}"; then
    rm -f "${err}"
    return 0
  fi
  cat "${err}" >&2
  rm -f "${err}"
  return 1
}

# Empty when the instance does not exist.
describe_instance() {
  local instance_id="${1:?instance_id is required}"

  aws_or_missing DBInstanceNotFound aws rds describe-db-instances \
    --db-instance-identifier "${instance_id}" \
    --query 'DBInstances[0].{status:DBInstanceStatus,class:DBInstanceClass,pending:PendingModifiedValues.DBInstanceClass}' \
    --output json
}

override_name() {
  echo "/veworld/${dead_color}/pg-instance-class/${1:?net is required}"
}

override_value() {
  aws_or_missing ParameterNotFound aws ssm get-parameter --name "$(override_name "${1:?net is required}")" \
    --query 'Parameter.Value' --output text
}

# Terraform reads this path, so a deploy keeps an `up` size until `down` deletes it.
record_override() {
  local net="${1:?net is required}"
  local class="${2:?class is required}"

  if [[ "${action}" == "up" ]]; then
    aws ssm put-parameter --name "$(override_name "${net}")" --type String \
      --value "${class}" --overwrite >/dev/null
  else
    aws_or_missing ParameterNotFound aws ssm delete-parameter --name "$(override_name "${net}")" || exit 1
  fi
}

# The switch-live-dns guard: a colour still sized for a sync must not take traffic.
assert_declared() {
  local net instance_id state class override failed=0

  for net in "${nets[@]}"; do
    instance_id="${dead_color}-${net}-pg"
    override="$(override_value "${net}")" || exit 1
    state="$(describe_instance "${instance_id}")" || exit 1
    class="$(jq -r '.class // empty' <<<"${state:-null}")"
    if [[ -n "${override}" || ( -n "${class}" && "${class}" != "$(declared_class "${net}")" ) ]]; then
      echo "::error::${instance_id} is ${class:-missing} with override '${override}'; run Scale Dead Prod Postgres with down first."
      failed=1
    fi
  done
  exit "${failed}"
}

# `aws rds wait db-instance-available` can return before the status leaves `available`.
wait_for_class() {
  local instance_id="${1:?instance_id is required}"
  local class="${2:?class is required}"
  local deadline=$((SECONDS + timeout_seconds))
  local state

  while ((SECONDS < deadline)); do
    state="$(describe_instance "${instance_id}")" || exit 1
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

[[ "${action}" == "assert-declared" ]] && assert_declared

declare -A targets=()
restarts=()
declare -A previous=()

for net in "${nets[@]}"; do
  instance_id="${dead_color}-${net}-pg"
  class="$(target_class "${net}")"

  state="$(describe_instance "${instance_id}")" || exit 1
  if [[ -z "${state}" ]]; then
    [[ "${PLAN_ONLY:-false}" == "true" ]] && continue
    record_override "${net}" "${class}"
    echo "${instance_id} does not exist; its next create uses ${class}."
    continue
  fi
  current="$(jq -r '.class' <<<"${state}")"
  status="$(jq -r '.status' <<<"${state}")"
  previous["${instance_id}"]="${current}"

  if [[ "${current}" != "${class}" && "${status}" != "available" ]]; then
    echo "${instance_id} is ${status}, not available; refusing to modify it."
    exit 1
  fi
  if [[ "${PLAN_ONLY:-false}" == "true" ]]; then
    [[ "${current}" != "${class}" ]] && restarts+=("${net}")
    continue
  fi
  record_override "${net}" "${class}"

  if [[ "${current}" == "${class}" ]]; then
    echo "${instance_id} is already ${class}; skipping."
    continue
  fi

  echo "Modifying ${instance_id}: ${current} -> ${class}"
  aws rds modify-db-instance \
    --db-instance-identifier "${instance_id}" \
    --db-instance-class "${class}" \
    --apply-immediately \
    >/dev/null
  targets["${instance_id}"]="${class}"
done

# The networks whose instance restarts, so the caller stops only their services.
if [[ "${PLAN_ONLY:-false}" == "true" ]]; then
  case "${#restarts[@]}" in
    0) restart_networks="" ;;
    1) restart_networks="${restarts[0]}" ;;
    *) restart_networks="both" ;;
  esac
  echo "restart_networks=${restart_networks}" >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
  exit 0
fi

for instance_id in "${!targets[@]}"; do
  wait_for_class "${instance_id}" "${targets[${instance_id}]}"
done

{
  echo "### Dead Prod Postgres Scale"
  echo "- Dead color: \`${dead_color}\`"
  echo "- Action: \`${action}\`"
  for net in "${nets[@]}"; do
    instance_id="${dead_color}-${net}-pg"
    echo "- \`${instance_id}\`: \`${previous[${instance_id}]:-missing}\` -> \`$(target_class "${net}")\`"
  done
  if [[ "${action}" == "up" ]]; then
    echo "- Note: deploys keep this size until \`down\`, and switch-live-dns refuses this colour until then."
  fi
} >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
