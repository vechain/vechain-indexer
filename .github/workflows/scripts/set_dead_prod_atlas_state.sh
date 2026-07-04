#!/usr/bin/env bash

set -euo pipefail

action="${ACTION:?ACTION is required}"
project_id="${MONGODB_ATLAS_PROJECT_ID:?MONGODB_ATLAS_PROJECT_ID is required}"
main_cluster="${MAIN_CLUSTER:?MAIN_CLUSTER is required}"
test_cluster="${TEST_CLUSTER:?TEST_CLUSTER is required}"

clusters=("${main_cluster}" "${test_cluster}")

cluster_state() {
  local name="${1:?cluster name required}"
  local describe_json
  describe_json="$(atlas clusters describe "${name}" --projectId "${project_id}" --output json 2>/dev/null || true)"
  if [[ -z "${describe_json}" ]]; then
    printf 'MISSING\n'
    return 0
  fi

  local paused state_name
  paused="$(printf '%s\n' "${describe_json}" | jq -r '.paused // false')"
  state_name="$(printf '%s\n' "${describe_json}" | jq -r '(.stateName // .state // "UNKNOWN") | ascii_upcase')"
  if [[ "${paused}" == "true" ]]; then
    printf 'PAUSED\n'
  else
    printf '%s\n' "${state_name}"
  fi
}

wait_for_state() {
  local name="${1:?cluster name required}"
  local target="${2:?target state required}"
  local timeout_seconds="${3:-1800}"
  local deadline=$(( $(date +%s) + timeout_seconds ))

  while (( $(date +%s) < deadline )); do
    local state
    state="$(cluster_state "${name}")"
    if [[ "${state}" == "${target}" ]]; then
      echo "${name} reached state ${target}."
      return 0
    fi
    echo "${name} state=${state}; waiting for ${target}..."
    sleep 30
  done

  echo "Timed out waiting for ${name} to reach ${target}." >&2
  return 1
}

pause_cluster() {
  local name="${1:?cluster name required}"
  local state
  state="$(cluster_state "${name}")"
  case "${state}" in
    MISSING)
      echo "${name} does not exist; nothing to pause."
      ;;
    PAUSED)
      echo "${name} already paused."
      ;;
    IDLE)
      echo "Pausing ${name}."
      atlas clusters pause "${name}" --projectId "${project_id}" >/dev/null
      wait_for_state "${name}" "PAUSED"
      ;;
    *)
      echo "${name} in transient state ${state}; refusing to pause." >&2
      return 1
      ;;
  esac
}

unpause_cluster() {
  local name="${1:?cluster name required}"
  local state
  state="$(cluster_state "${name}")"
  case "${state}" in
    MISSING)
      echo "${name} does not exist; nothing to unpause."
      ;;
    IDLE)
      echo "${name} already running."
      ;;
    PAUSED)
      echo "Unpausing ${name}."
      atlas clusters start "${name}" --projectId "${project_id}" >/dev/null
      wait_for_state "${name}" "IDLE"
      ;;
    *)
      echo "${name} in transient state ${state}; refusing to unpause." >&2
      return 1
      ;;
  esac
}

describe() {
  local main_state test_state
  main_state="$(cluster_state "${main_cluster}")"
  test_state="$(cluster_state "${test_cluster}")"

  echo "cluster=${main_cluster} state=${main_state}"
  echo "cluster=${test_cluster} state=${test_state}"

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "main_cluster_state=${main_state}"
      echo "test_cluster_state=${test_state}"
    } >> "${GITHUB_OUTPUT}"
  fi

  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      echo "### Dead Prod Atlas Cluster State"
      echo "- ${main_cluster}: \`${main_state}\`"
      echo "- ${test_cluster}: \`${test_state}\`"
    } >> "${GITHUB_STEP_SUMMARY}"
  fi
}

case "${action}" in
  describe)
    describe
    ;;
  pause)
    for name in "${clusters[@]}"; do
      pause_cluster "${name}"
    done
    ;;
  unpause)
    for name in "${clusters[@]}"; do
      unpause_cluster "${name}"
    done
    ;;
  *)
    echo "Unsupported action ${action}" >&2
    exit 1
    ;;
esac
