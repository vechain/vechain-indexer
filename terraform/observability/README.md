# Observability Terraform Stack

Amazon Managed Prometheus (AMP) + Amazon Managed Grafana (AMG) workspaces for the veworld-indexer observability migration. Phase 2 of the plan captured in `notes/observability-migration.md`.

## What this stack contains

- One AMP workspace with CloudWatch log group for ingestion logs.
- One AMG workspace at Grafana v12, `permission_type = CUSTOMER_MANAGED`, authenticated via SAML.
- Workspace IAM role that lets AMG read AMP and CloudWatch (metrics + logs) and manage silences.
- Terraform-provider service account with an admin token in Secrets Manager, rotated every 25 days.
- Okta SAML configuration — gated on `okta_saml_metadata_url` being non-empty. Empty by default until the Okta app is registered.
- **Alerting** — AMP rule groups + Alertmanager definition, delivered to Slack via SNS → bridge Lambda. See `alerts.tf` and `locals.tf` for rules and the alertmanager template. Ported from `agent-marketplace/infra/terraform/observability-aws`.
- **Backup inventory** — a scheduled Lambda that turns RDS snapshot state into CloudWatch metrics. See `backups.tf` and the section below.
- **SNS topic shared with CloudWatch** — the same topic receives the CloudWatch alarms defined in `terraform/api/alarms.tf`; the topic policy here grants `cloudwatch.amazonaws.com` publish rights.

Deliberately not in this stack (yet): dashboards, scrape targets, recording rules. Dashboards live in `terraform/observability-grafana`.

## Alert delivery

Two producers, one pipeline: **AMP Alertmanager / CloudWatch alarms → SNS (`aws_sns_topic.alerts`) → `sns_to_slack` Lambda → Slack webhook**.

AMP rules cover what the services report about themselves. Those series vanish rather than breach when a task dies, so the CloudWatch alarms in `terraform/api/alarms.tf` cover the same ground from ECS and ALB metrics AWS publishes on our behalf. The topic policy in `alerts.tf` must allow both principals — an unlisted service principal is denied, because the policy replaces the topic's default account-owner policy.

Alertmanager pre-renders its Slack body and the Lambda forwards it verbatim. CloudWatch publishes JSON with no labels, so the alarm's `AlarmDescription` carries the whole thing as `"[env/deployment/network] service: Title — summary."` and `_render_cloudwatch_alarm` splits it on the first `" — "`. Keep that convention when adding alarms or the header renders as one long line.

Slack webhook value comes in via `TF_VAR_slack_webhook_url` (marked sensitive). While unset, the secret holds the literal string `placeholder` and the Lambda no-ops, so the plumbing can apply before the webhook exists. Populate the workflow secret and reapply to switch delivery on.

Alert rules are stamped without an explicit `env`/`deployment`/`network`/`service` label — those come through from the underlying series' external_labels (set by the sidecar). Aggregating alerts (e.g. `sum by (...) rate(...)`) must include those labels in the `by` clause or Alertmanager `.CommonLabels` will drop them.

## Live-only alerts

The services do not know which colour is live, and neither do the rules: the Route53 `<network>.live.<zone>` record is the only definition, and it changes at runtime. Rules that should only page for the live colour carry `live_only: "true"`. Alertmanager forwards `live_only`, `deployment` and `network` as SNS message attributes, and `sns_to_slack.py` resolves the live record for that network (cached for a minute) and drops the message when the colours differ. Rules stay colour-agnostic, so Grafana still shows a warm dead colour falling behind; only Slack is gated. On a Route53 error or missing attributes the Lambda forwards rather than drops.

Two consequences: a suppressed alert is re-evaluated on every Alertmanager repeat, so a colour that goes live mid-incident starts paging on the next repeat; and an alert that was firing on the colour that just went dead never gets a Slack "resolved", because that is dropped too.

`LiveIndexerBehindHead`, `LiveIndexerThorHeadStale` and `LiveIndexerTelemetryMissing` use it. The last one is `absent_over_time` per colour and network, which only works behind a delivery-time gate: at rule time, a cold dead colour and a broken live one look the same.

## Backup inventory

The Postgres backups are RDS automated snapshots — `backup_retention_period = 7` and a
`06:00-07:00` window, set in `terraform/api/postgres.tf`. RDS publishes how much backup storage it
bills for and nothing at all about the snapshots themselves: no age, no count, no duration, no
progress. `.github/workflows/scripts/restore_dead_prod_pg_snapshots.sh` rebuilds a dead colour from
the newest available snapshot, so without this the first sign that backups had stopped would be a
dead colour restored onto week-old data.

`lambda/pg_backup_inventory.py` runs every 5 minutes, finds every RDS instance tagged
`Backup = <project>-pg`, and publishes under the `VeWorld/RDSBackups` namespace, dimensioned by
`DBInstanceIdentifier`. Every metric covers **automated snapshots only**, because that is what
`restore_dead_prod_pg_snapshots.sh` selects (`--snapshot-type automated`); a manual snapshot taken
by hand would otherwise read as a healthy backup while the one a restore picks went stale. Manual
snapshots are still logged, so they appear in the dashboard table:

| Metric | Unit | What it is |
| --- | --- | --- |
| `LastBackupStartedAge` | Seconds | Age of the newest snapshot of any status — how long ago a backup last *began* |
| `NewestSnapshotAge` | Seconds | Age of the newest `available` snapshot — what a restore would start from |
| `OldestSnapshotAge` | Seconds | Age of the oldest, i.e. the far edge of the restore window |
| `SnapshotsAvailable` | Count | Automated and manual snapshots that are restorable now |
| `SnapshotsInProgress` | Count | Snapshots in `creating` |
| `SnapshotProgress` | Percent | Lowest `PercentProgress` among those, published only while one is running |
| `NewestSnapshotAllocatedStorage` | Gigabytes | Volume size the newest snapshot covers |
| `LastBackupDuration` | Seconds | Most recent completed backup's wall time |
| `InstancesInventoried` | Count | Instances the run found; undimensioned |

It also logs one pipe-delimited `rds_snapshot|…` line per snapshot per run, which is what the
Grafana table reads back through a Logs Insights `parse`. Pipes rather than JSON because Lambda
wraps stdout in its own envelope, so JSON auto-discovery does not fire on the payload.

Things worth knowing before reading a number off it:

- **There is no backup size available, from any API.** RDS exposes no per-snapshot size, and the
  question is not well posed anyway: the first snapshot of an instance is a full copy and every
  later one is incremental against it. The three metrics that would answer it —
  `TotalBackupStorageBilled`, `BackupRetentionPeriodStorageUsed`, `SnapshotStorageUsed` — are
  documented under `AWS/RDS` but publish **no datapoints for non-Aurora instances**; checked
  against this account, dimensioned by `DBInstanceIdentifier` and undimensioned, both empty while
  `FreeStorageSpace` returned data over the same window. Do not add panels for them. What is left
  is `NewestSnapshotAllocatedStorage`, the *volume* a snapshot covers, and that is what the
  dashboard shows. True consumed bytes are a billing figure only, reachable through Cost Explorer
  (`ce:GetCostAndUsage`) — deliberately not wired up here, since Cost Explorer bills per request
  and would need its own daily schedule rather than the 5-minute poll.
- **Duration comes from events, not from the snapshot.** A snapshot carries a start time and no
  end time, so the `Backing up DB instance` / `Finished DB Instance backup` event pair is the only
  completion signal. `DescribeEvents` retains 14 days; an instance with no backup in that window
  publishes no duration even though its snapshots are fine. The events are matched on message
  text, which is what will break first if AWS rewords them.
- **Started and restorable are different clocks, and the gap is the backup's run time.** A snapshot
  is not restorable until it finishes; mainnet's backups currently run for hours. So
  `NewestSnapshotAge` climbs past 25 hours every morning while that day's backup is still running,
  and only drops when it completes. `LastBackupStartedAge` counts the in-flight snapshot, so it
  resets when the backup begins. Alarm on the second, not the first.
- **The event window is 14 days, not 24 hours.** A backup that starts at 06:00 and finishes at
  noon puts its two events more than a day apart from the next poll's point of view, so a 24-hour
  `DescribeEvents` window loses the pair and `LastBackupDuration` disappears for part of every day.
  The events are also sorted by date before pairing — the API documents no ordering guarantee.
- **The metric is a step, not a sample per backup.** Every metric here is republished on each
  5-minute poll, so a Grafana line holds its last value between backups rather than drawing one
  point a day. Read the step changes.
- **A failed run publishes nothing.** The handler lets exceptions propagate rather than publishing
  a partial set, because a half-published run reads as a healthy one. That is what makes the
  `missing` treatment on the staleness alarm safe — see below.

### Alarms

Three, deliberately split by what each can see. None is threshold-ed on how long a backup takes,
because that would page every morning while a multi-hour run is still going.

`pg-backup-not-starting`, per instance, in `terraform/api/alarms.tf` with the other Postgres alarms
and the per-colour Slack header they share. `LastBackupStartedAge` over 26 hours against a daily
schedule: RDS has stopped beginning backups. Duration-independent, so it is the one that should
fire first for the common failure.

`pg-backup-stale`, its companion, covers backups that start on time and never finish — which the
first alarm cannot see. `NewestSnapshotAge` over 48 hours is two whole cycles, well clear of the
hours a run currently takes. Raise it if backup duration ever approaches a day; a run that stalls
part-way is visible long before that in the "Backup in progress" panel.

Both use `treat_missing_data = "ignore"`, which is the setting that actually holds the last state —
`missing` sends an alarm to `INSUFFICIENT_DATA` once every point in the window is absent. Holding is
what a dead-colour restore needs, since it replaces the instance wholesale and the inventory is
blind for as long as that takes. `pg-backup-inventory-stalled` lives
here and is what makes that safe: `InstancesInventoried < 1` for 30 minutes, missing data breaching.
Because a failed run publishes nothing, that one alarm covers a run that threw and a schedule that
stopped. It does **not** catch a single instance dropping out of the inventory — the count would
fall from two to one and stay above the threshold — but the `Backup` tag is set on the instance
resource in `terraform/api/postgres.tf`, so an instance can only lose it through a terraform
change, not through runtime drift.

`VeWorld/RDSBackups` is written out as a literal in all three files that name it — `backups.tf`,
the api alarms, and `dashboards/overview.json` — rather than crossing stacks as an output. An output
would make every `terraform/api` plan fail until this stack had been applied, and a PR only ever
plans this stack, so the api check could not go green before merge. Grep the namespace before
renaming it.


## Apply order

`terraform/api` reads `alerts_topic_arn` from this stack's remote state and its alarms publish under this stack's topic policy. Apply this stack first when either changes; the api plan fails loudly if the output is missing.

## Usage

```bash
cd terraform/observability
terraform init -backend-config=environments/prod.config
terraform workspace select -or-create prod
terraform plan
terraform apply
```

## Enabling Okta SAML later

1. Register the Amazon Managed Grafana app in Okta (SAML 2.0), add group attribute statements for `admin` and `editor`.
2. Populate `okta_saml_metadata_url`, `grafana_admin_okta_groups`, and `grafana_editor_okta_groups` in `environments/prod.yml`.
3. `terraform apply` — the SAML configuration is created and SSO becomes live.

## AMG service-account token rotation

`time_rotating.grafana_sa_token` triggers a token replacement every 25 days, ahead of AMG's 30-day TTL cap. Downstream stacks that read the secret at apply time do not need to change — they resolve through Secrets Manager, which is versioned in place.

## AMP retention

AMP caps metric retention at 150 days. If a longer window is needed for compliance or archival we introduce an S3 snapshot sidecar (out of scope for this stack).

## Operational notes

### Apply cadence

`time_rotating.grafana_sa_token` only advances during a `terraform apply` on this stack. AMG service-account tokens have a hard 30-day TTL. If nobody applies for 30 days the token in Secrets Manager becomes invalid and any downstream stack that reads it (e.g. the dashboards-as-code stack in a later phase) will fail authentication.

Nothing reads the token today, so short-term drift is harmless — the next apply mints a new token and `create_before_destroy` swaps the secret value in place. Before we land a stack that consumes the token, we should either add a scheduled `terraform apply` workflow (~every 20 days) or lower the `rotation_days` value in step with the actual apply cadence.

### Sensitive state

`aws_grafana_workspace_service_account_token.terraform.key` and `aws_secretsmanager_secret_version.amg_sa_token.secret_string` are both stored in plaintext in the terraform state file, per how the terraform state model works. This matches how every other secret this repo manages via terraform is stored (Postgres passwords, WAF bypass tokens). The mitigation is at the state-backend layer: read access to `s3://veworld-indexer-terraform-state-prod` is scoped to the deploy OIDC role. Anyone with access to the bucket effectively has access to those secrets.

## Backend locking

Enabled via `use_lockfile = true` on the S3 backend (S3-native locking, no DynamoDB). Matches `terraform/api` and `terraform/vpc` after the repo-wide toolchain bump to terraform 1.13.5.
