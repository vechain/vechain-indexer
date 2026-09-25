"""Inventory of the Postgres backups RDS takes for us, as CloudWatch metrics and log lines.

RDS publishes how much backup storage it bills for and nothing about the snapshots themselves,
while `restore_dead_prod_pg_snapshots.sh` rebuilds the dead colour from the newest one — so a
backup that quietly stopped is discovered at the moment it is needed. Every instance tagged
Backup=<project>-pg is polled for its snapshots and its backup events. See the "Backup inventory"
section of terraform/observability/README.md.
"""

import datetime as dt
import json
import os
import xml.etree.ElementTree as ET

import boto3

_rds = boto3.client("rds")
_cloudwatch = boto3.client("cloudwatch")

_NAMESPACE = os.environ["METRIC_NAMESPACE"]
_TAG_KEY = os.environ["BACKUP_TAG_KEY"]
_TAG_VALUE = os.environ["BACKUP_TAG_VALUE"]
_EVENT_WINDOW_MINUTES = 20160  # 14 days, all DescribeEvents retains
_JUST_FINISHED_SECONDS = 900  # three polls, so a late or retried run still lands the 100
_RUN_MATCH_SECONDS = 300  # an automated snapshot's create time trails its run's start by ~1s

# Filled from each DescribeDBInstances response: identifier -> (status, percent).
_storage_operations: dict[str, tuple[str, float]] = {}


def _record_storage_operations(http_response, **_kwargs) -> None:
    """Reads StorageOperation* from the raw XML, which botocore before 1.43.62 parses away."""
    for instance in ET.fromstring(http_response.content).iter():
        if not instance.tag.endswith("}DBInstance"):
            continue
        fields = {child.tag.split("}")[-1]: child.text for child in instance}
        if fields.get("StorageOperationStatus"):
            _storage_operations[fields["DBInstanceIdentifier"]] = (
                fields["StorageOperationStatus"],
                float(fields.get("StorageOperationPercentProgress") or 0),
            )


_rds.meta.events.register("after-call.rds.DescribeDBInstances", _record_storage_operations)


def _tagged_instances() -> list[str]:
    identifiers, seen = [], set()
    for page in _rds.get_paginator("describe_db_instances").paginate():
        for instance in page["DBInstances"]:
            tags = {tag["Key"]: tag["Value"] for tag in instance.get("TagList", [])}
            if tags.get(_TAG_KEY) == _TAG_VALUE:
                identifiers.append(instance["DBInstanceIdentifier"])
            elif _TAG_KEY in tags:
                seen.add(tags[_TAG_KEY])
    if not identifiers:
        # Name the mismatch rather than leaving a bare zero to work backwards from.
        print(f"no instance tagged {_TAG_KEY}={_TAG_VALUE}; values seen: {sorted(seen) or 'none'}")
    return identifiers


def _snapshots(identifier: str) -> list[dict]:
    # Manual snapshots are included because an operator-taken one is a restore source too.
    found = []
    for snapshot_type in ("automated", "manual"):
        pages = _rds.get_paginator("describe_db_snapshots").paginate(
            DBInstanceIdentifier=identifier, SnapshotType=snapshot_type
        )
        for page in pages:
            found.extend(page["DBSnapshots"])
    return found


def _backup_events(identifier: str) -> list[dict]:
    """The instance's `backup` events oldest first.

    RDS exposes no completion time on a snapshot, so these events are the only source for both
    how long a run took and when it ended. A multi-hour backup can put its start and finish in
    different days, so the window is the full 14 days DescribeEvents retains.
    """
    events = _rds.describe_events(
        SourceIdentifier=identifier,
        SourceType="db-instance",
        EventCategories=["backup"],
        Duration=_EVENT_WINDOW_MINUTES,
        MaxRecords=100,
    )["Events"]
    return sorted(events, key=lambda e: e["Date"])


def _automated_runs(events: list[dict], snapshots: list[dict]) -> list[tuple[dt.datetime, dt.datetime]]:
    """(started, finished) of each completed run that produced an automated snapshot, oldest first.

    The events do not say which kind of snapshot a run took, so a manual one, such as the destroy
    workflow's final snapshot, is told apart by having no automated snapshot created as it began.
    """
    created = [
        s["SnapshotCreateTime"]
        for s in snapshots
        if s.get("SnapshotType") == "automated" and s.get("SnapshotCreateTime")
    ]
    runs, started = [], None
    for event in events:
        if _is_start(event):
            started = event["Date"]
        elif _is_finish(event) and started is not None:
            if any(0 <= (c - started).total_seconds() <= _RUN_MATCH_SECONDS for c in created):
                runs.append((started, event["Date"]))
            started = None
    return runs


def _is_start(event: dict) -> bool:
    return event.get("Message", "").lower().startswith("backing up")


def _is_finish(event: dict) -> bool:
    return "finished" in event.get("Message", "").lower()


def _datum(name: str, value: float, unit: str, identifier: str | None = None) -> dict:
    datum = {"MetricName": name, "Value": value, "Unit": unit}
    if identifier:
        datum["Dimensions"] = [{"Name": "DBInstanceIdentifier", "Value": identifier}]
    return datum


def _log_snapshot(identifier: str, snapshot: dict, age_seconds: float | None) -> None:
    # Pipe-delimited rather than JSON: Lambda wraps stdout in its own envelope, so a Logs
    # Insights `parse` pattern reads this back where JSON auto-discovery would not.
    created = snapshot.get("SnapshotCreateTime")
    fields = (
        "rds_snapshot",
        identifier,
        snapshot["DBSnapshotIdentifier"],
        snapshot.get("SnapshotType", ""),
        snapshot.get("Status", ""),
        created.isoformat() if created else "",
        "" if age_seconds is None else round(age_seconds / 3600.0, 2),
        snapshot.get("AllocatedStorage", ""),
        snapshot.get("PercentProgress", ""),
    )
    print("|".join(str(field) for field in fields))


def _instance_metrics(identifier: str, now: dt.datetime) -> list[dict]:
    snapshots = _snapshots(identifier)
    runs = _automated_runs(_backup_events(identifier), snapshots)
    # A restored volume lazy-loads from S3 until initialized; RDS drops both fields once it is.
    status, percent = _storage_operations.get(identifier, ("", 100.0))
    data = [_datum("StorageInitialized", percent if status == "Initializing" else 100.0, "Percent", identifier)]

    # Metrics cover automated snapshots only: restore_dead_prod_pg_snapshots.sh passes
    # --snapshot-type automated, so a manual snapshot taken by hand would otherwise read as a
    # healthy backup while the one a restore actually picks went stale. Both are logged.
    ages, started_ages, in_progress = {}, [], []
    for snapshot in snapshots:
        created = snapshot.get("SnapshotCreateTime")
        age = (now - created).total_seconds() if created else None
        _log_snapshot(identifier, snapshot, age)
        if snapshot.get("SnapshotType") != "automated":
            continue
        if snapshot.get("Status") == "creating":
            in_progress.append(snapshot)
        # A snapshot has no create time until it has one; treat those as in progress only.
        if age is not None:
            started_ages.append(age)
            if snapshot.get("Status") == "available":
                ages[snapshot["DBSnapshotIdentifier"]] = (age, snapshot)
    data.append(_datum("SnapshotsAvailable", len(ages), "Count", identifier))
    data.append(_datum("SnapshotsInProgress", len(in_progress), "Count", identifier))
    if in_progress:
        progress = min(s.get("PercentProgress", 0) for s in in_progress)
        data.append(_datum("SnapshotProgress", progress, "Percent", identifier))
    else:
        # RDS flips a snapshot from `creating` to `available` without ever publishing 100, so the
        # series would otherwise end on whichever mid-run sample the last poll caught. The finish
        # event is what closes it off.
        finished = runs[-1][1] if runs else None
        if finished is not None and (now - finished).total_seconds() <= _JUST_FINISHED_SECONDS:
            data.append(_datum("SnapshotProgress", 100, "Percent", identifier))

    # Counts a snapshot still being taken, so this tracks "a backup started" independently of how
    # long one runs — which is what the staleness alarm needs.
    if started_ages:
        data.append(_datum("LastBackupStartedAge", min(started_ages), "Seconds", identifier))

    if ages:
        newest_age, newest = min(ages.values(), key=lambda pair: pair[0])
        data.append(_datum("NewestSnapshotAge", newest_age, "Seconds", identifier))
        data.append(_datum("OldestSnapshotAge", max(age for age, _ in ages.values()), "Seconds", identifier))
        # The volume the snapshot covers, not the bytes it consumes: RDS publishes no snapshot
        # size, and no backup-storage metric at all for non-Aurora instances.
        data.append(
            _datum("NewestSnapshotAllocatedStorage", newest.get("AllocatedStorage", 0), "Gigabytes", identifier)
        )

    if runs:
        started, finished = runs[-1]
        data.append(_datum("LastBackupDuration", (finished - started).total_seconds(), "Seconds", identifier))
    return data


def _log_detached_snapshots(identifiers: list[str], now: dt.datetime) -> None:
    """Logs the tagged snapshots whose instance is gone, such as a destroyed colour's final one.

    The per-instance listing cannot reach them, so without this the table loses the only
    remaining copy of a colour at the moment that colour is destroyed.
    """
    for page in _rds.get_paginator("describe_db_snapshots").paginate(SnapshotType="manual"):
        for snapshot in page["DBSnapshots"]:
            tags = {tag["Key"]: tag["Value"] for tag in snapshot.get("TagList", [])}
            if tags.get(_TAG_KEY) != _TAG_VALUE or snapshot["DBInstanceIdentifier"] in identifiers:
                continue
            created = snapshot.get("SnapshotCreateTime")
            age = (now - created).total_seconds() if created else None
            _log_snapshot(snapshot["DBInstanceIdentifier"], snapshot, age)


def handler(_event: dict, _context) -> dict:
    # Failures propagate: a half-published run would otherwise read as a healthy one, and the
    # InstancesInventoried alarm treats the resulting gap as breaching.
    now = dt.datetime.now(dt.timezone.utc)
    _storage_operations.clear()
    identifiers = _tagged_instances()

    data = [_datum("InstancesInventoried", len(identifiers), "Count")]
    for identifier in identifiers:
        data.extend(_instance_metrics(identifier, now))
    _log_detached_snapshots(identifiers, now)

    for batch in (data[i : i + 1000] for i in range(0, len(data), 1000)):
        _cloudwatch.put_metric_data(Namespace=_NAMESPACE, MetricData=batch)

    print(json.dumps({"instances": identifiers, "metrics": len(data)}))
    return {"instances": len(identifiers), "metrics": len(data)}
