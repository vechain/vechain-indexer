#!/usr/bin/env bash

# Takes a manual snapshot of each dead-colour Postgres instance before it is destroyed.
# The instance skips its final snapshot and its automated backups go with it; the newest of these is kept.

set -euo pipefail

dead_color="${DEAD_COLOR:?DEAD_COLOR is required}"
timeout_seconds="${TIMEOUT_SECONDS:-7200}"
stamp="$(date -u +%Y%m%d-%H%M)"

err_file="$(mktemp)"
trap 'rm -f "${err_file}"' EXIT

# Expired credentials must not read as "no instance", or the destroy runs with nothing kept.
instance_status() {
  aws rds describe-db-instances --db-instance-identifier "${1}" \
    --query 'DBInstances[0].DBInstanceStatus' --output text 2>"${err_file}" && return 0
  grep -q DBInstanceNotFound "${err_file}" && return 0
  echo "Could not look up ${1}."
  cat "${err_file}"
  exit 1
}

declare -A snapshots=()
pruned=()

for net in main test; do
  instance_id="${dead_color}-${net}-pg"
  status="$(instance_status "${instance_id}")"
  if [[ -z "${status}" ]]; then
    echo "${instance_id} does not exist; nothing to snapshot."
    continue
  fi
  if [[ "${status}" != "available" ]]; then
    echo "${instance_id} is ${status}, not available; refusing to destroy it unsnapshotted."
    exit 1
  fi

  snapshot_id="${instance_id}-final-${stamp}"
  echo "Snapshotting ${instance_id} as ${snapshot_id}"
  aws rds create-db-snapshot \
    --db-instance-identifier "${instance_id}" \
    --db-snapshot-identifier "${snapshot_id}" \
    >/dev/null
  snapshots["${instance_id}"]="${snapshot_id}"
done

# `aws rds wait db-snapshot-available` gives up after 30 minutes.
deadline=$((SECONDS + timeout_seconds))
for instance_id in "${!snapshots[@]}"; do
  snapshot_id="${snapshots[${instance_id}]}"
  while true; do
    state="$(aws rds describe-db-snapshots --db-snapshot-identifier "${snapshot_id}" \
      --query 'DBSnapshots[0].[Status,PercentProgress]' --output text)"
    read -r status progress <<<"${state}"
    [[ "${status}" == "available" ]] && break
    if [[ "${status}" != "creating" ]]; then
      echo "${snapshot_id} is ${status}; refusing to destroy ${instance_id}."
      exit 1
    fi
    if ((SECONDS >= deadline)); then
      echo "${snapshot_id} was not available within ${timeout_seconds}s."
      exit 1
    fi
    echo "${snapshot_id}: ${status} ${progress}%"
    sleep 30
  done
  echo "${snapshot_id} is available."
  # The instance sits in `backing-up` a little longer, and RDS refuses to delete it until it leaves.
  aws rds wait db-instance-available --db-instance-identifier "${instance_id}"

  # Only the newest is kept for DR, and only once it is available.
  older="$(aws rds describe-db-snapshots --db-instance-identifier "${instance_id}" --snapshot-type manual \
    --query "DBSnapshots[?starts_with(DBSnapshotIdentifier, '${instance_id}-final-') && DBSnapshotIdentifier != '${snapshot_id}'].DBSnapshotIdentifier" \
    --output text)"
  for old_id in ${older}; do
    echo "Deleting superseded ${old_id}"
    aws rds delete-db-snapshot --db-snapshot-identifier "${old_id}" >/dev/null
    pruned+=("${old_id}")
  done
done

{
  echo "### Dead Prod Postgres Final Snapshots"
  for instance_id in "${!snapshots[@]}"; do
    echo "- \`${instance_id}\` kept as \`${snapshots[${instance_id}]}\`"
  done
  for old_id in "${pruned[@]+"${pruned[@]}"}"; do
    echo "- \`${old_id}\` deleted, superseded"
  done
  if [[ "${#snapshots[@]}" -eq 0 ]]; then
    echo "- No instance existed"
  fi
} >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
