#!/usr/bin/env bash

set -euo pipefail

source_color="${SOURCE_COLOR:?SOURCE_COLOR is required}"
target_color="${TARGET_COLOR:?TARGET_COLOR is required}"
source_main_cluster="${SOURCE_MAIN_CLUSTER:?SOURCE_MAIN_CLUSTER is required}"
source_test_cluster="${SOURCE_TEST_CLUSTER:?SOURCE_TEST_CLUSTER is required}"
target_main_cluster="${TARGET_MAIN_CLUSTER:?TARGET_MAIN_CLUSTER is required}"
target_test_cluster="${TARGET_TEST_CLUSTER:?TARGET_TEST_CLUSTER is required}"
source_project_id="${SOURCE_PROJECT_ID:?SOURCE_PROJECT_ID is required}"
target_project_id="${TARGET_PROJECT_ID:?TARGET_PROJECT_ID is required}"
summary_path="${RESTORE_SUMMARY_PATH:?RESTORE_SUMMARY_PATH is required}"
output_dir="$(dirname "${summary_path}")"

mkdir -p "${output_dir}"

extract_latest_completed_snapshot_id() {
  local cluster_name="${1:?cluster_name is required}"
  local project_id="${2:?project_id is required}"

  atlas backups snapshots list "${cluster_name}" \
    --projectId "${project_id}" \
    --output json \
    | jq -r '
      (if type == "array" then . else (.results // .items // []) end)
      | map(select(((.status // .statusName // "") | ascii_downcase) == "completed"))
      | sort_by(.createdAt // .created_at // .created)
      | last
      | (.id // .snapshotId // empty)
    '
}

start_restore() {
  local source_cluster="${1:?source_cluster is required}"
  local snapshot_id="${2:?snapshot_id is required}"
  local target_cluster="${3:?target_cluster is required}"
  local project_id="${4:?project_id is required}"
  local target_project="${5:?target_project is required}"
  local start_output restore_id

  start_output="$(atlas backups restores start automated \
    --clusterName "${source_cluster}" \
    --snapshotId "${snapshot_id}" \
    --projectId "${project_id}" \
    --targetClusterName "${target_cluster}" \
    --targetProjectId "${target_project}" \
    --output json)"

  printf '%s\n' "${start_output}"

  restore_id="$(printf '%s\n' "${start_output}" | jq -r '
    .id
    // .restoreId
    // .results[0].id
    // .results[0].restoreId
    // .items[0].id
    // .items[0].restoreId
    // empty
  ')"
  if [[ -z "${restore_id}" ]]; then
    echo "Failed to parse restore job ID for ${source_cluster}."
    exit 1
  fi

  printf '%s\n' "${restore_id}"
}

watch_restore() {
  local restore_id="${1:?restore_id is required}"
  local source_cluster="${2:?source_cluster is required}"
  local project_id="${3:?project_id is required}"

  atlas backups restores watch "${restore_id}" \
    --clusterName "${source_cluster}" \
    --projectId "${project_id}"
}

describe_restore() {
  local restore_id="${1:?restore_id is required}"
  local source_cluster="${2:?source_cluster is required}"
  local project_id="${3:?project_id is required}"

  atlas backups restores describe "${restore_id}" \
    --clusterName "${source_cluster}" \
    --projectId "${project_id}" \
    --output json
}

echo "Selecting latest completed Atlas snapshots for ${source_color}."
test_snapshot_id="$(extract_latest_completed_snapshot_id "${source_test_cluster}" "${source_project_id}")"
main_snapshot_id="$(extract_latest_completed_snapshot_id "${source_main_cluster}" "${source_project_id}")"

if [[ -z "${test_snapshot_id}" || -z "${main_snapshot_id}" ]]; then
  echo "Unable to find completed snapshots for both source clusters."
  exit 1
fi

echo "Starting restore for ${source_test_cluster} -> ${target_test_cluster}"
test_restore_output="$(start_restore "${source_test_cluster}" "${test_snapshot_id}" "${target_test_cluster}" "${source_project_id}" "${target_project_id}")"
test_restore_id="$(printf '%s\n' "${test_restore_output}" | tail -n 1)"

echo "Starting restore for ${source_main_cluster} -> ${target_main_cluster}"
main_restore_output="$(start_restore "${source_main_cluster}" "${main_snapshot_id}" "${target_main_cluster}" "${source_project_id}" "${target_project_id}")"
main_restore_id="$(printf '%s\n' "${main_restore_output}" | tail -n 1)"

echo "Watching restore ${test_restore_id} (${source_test_cluster})"
watch_restore "${test_restore_id}" "${source_test_cluster}" "${source_project_id}"

echo "Watching restore ${main_restore_id} (${source_main_cluster})"
watch_restore "${main_restore_id}" "${source_main_cluster}" "${source_project_id}"

test_restore_json="$(describe_restore "${test_restore_id}" "${source_test_cluster}" "${source_project_id}")"
main_restore_json="$(describe_restore "${main_restore_id}" "${source_main_cluster}" "${source_project_id}")"

printf '%s\n' "${test_restore_json}" > "${output_dir}/test-restore.json"
printf '%s\n' "${main_restore_json}" > "${output_dir}/main-restore.json"

jq -n \
  --arg source_color "${source_color}" \
  --arg target_color "${target_color}" \
  --arg source_project_id "${source_project_id}" \
  --arg target_project_id "${target_project_id}" \
  --arg test_snapshot_id "${test_snapshot_id}" \
  --arg main_snapshot_id "${main_snapshot_id}" \
  --arg test_restore_id "${test_restore_id}" \
  --arg main_restore_id "${main_restore_id}" \
  --arg source_test_cluster "${source_test_cluster}" \
  --arg source_main_cluster "${source_main_cluster}" \
  --arg target_test_cluster "${target_test_cluster}" \
  --arg target_main_cluster "${target_main_cluster}" \
  --argjson test_restore "${test_restore_json}" \
  --argjson main_restore "${main_restore_json}" \
  '{
    sourceColor: $source_color,
    targetColor: $target_color,
    sourceProjectId: $source_project_id,
    targetProjectId: $target_project_id,
    generatedAt: now | todate,
    restores: {
      testnet: {
        sourceCluster: $source_test_cluster,
        targetCluster: $target_test_cluster,
        snapshotId: $test_snapshot_id,
        restoreJobId: $test_restore_id,
        describe: $test_restore
      },
      mainnet: {
        sourceCluster: $source_main_cluster,
        targetCluster: $target_main_cluster,
        snapshotId: $main_snapshot_id,
        restoreJobId: $main_restore_id,
        describe: $main_restore
      }
    }
  }' > "${summary_path}"

{
  echo "### Dead Prod Atlas Restore"
  echo "- Source color: \`${source_color}\`"
  echo "- Target color: \`${target_color}\`"
  echo "- Testnet snapshot: \`${test_snapshot_id}\`"
  echo "- Testnet restore job: \`${test_restore_id}\`"
  echo "- Mainnet snapshot: \`${main_snapshot_id}\`"
  echo "- Mainnet restore job: \`${main_restore_id}\`"
  echo "- Summary artifact: \`${summary_path}\`"
} >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
