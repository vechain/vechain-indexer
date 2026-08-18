# Observability Terraform Stack

Amazon Managed Prometheus (AMP) + Amazon Managed Grafana (AMG) workspaces for the veworld-indexer observability migration. Phase 2 of the plan captured in `notes/observability-migration.md`.

## What this stack contains

- One AMP workspace with CloudWatch log group for ingestion logs.
- One AMG workspace at Grafana v12, `permission_type = CUSTOMER_MANAGED`, authenticated via SAML.
- Workspace IAM role that lets AMG read AMP and CloudWatch (metrics + logs) and manage silences.
- Terraform-provider service account with an admin token in Secrets Manager, rotated every 25 days.
- Okta SAML configuration — gated on `okta_saml_metadata_url` being non-empty. Empty by default until the Okta app is registered.
- **Alerting** — AMP rule groups + Alertmanager definition, delivered to Slack via SNS → bridge Lambda. See `alerts.tf` and `locals.tf` for rules and the alertmanager template. Ported from `agent-marketplace/infra/terraform/observability-aws`.
- **SNS topic shared with CloudWatch** — the same topic receives the CloudWatch alarms defined in `terraform/api/alarms.tf`; the topic policy here grants `cloudwatch.amazonaws.com` publish rights.

Deliberately not in this stack (yet): dashboards, scrape targets, recording rules. Dashboards live in `terraform/observability-grafana`.

## Alert delivery

Two producers, one pipeline: **AMP Alertmanager / CloudWatch alarms → SNS (`aws_sns_topic.alerts`) → `sns_to_slack` Lambda → Slack webhook**.

AMP rules cover what the services report about themselves. Those series vanish rather than breach when a task dies, so the CloudWatch alarms in `terraform/api/alarms.tf` cover the same ground from ECS and ALB metrics AWS publishes on our behalf. The topic policy in `alerts.tf` must allow both principals — an unlisted service principal is denied, because the policy replaces the topic's default account-owner policy.

Alertmanager pre-renders its Slack body and the Lambda forwards it verbatim. CloudWatch publishes JSON with no labels, so the alarm's `AlarmDescription` carries the whole thing as `"[env/deployment/network] service: Title — summary."` and `_render_cloudwatch_alarm` splits it on the first `" — "`. Keep that convention when adding alarms or the header renders as one long line.

Slack webhook value comes in via `TF_VAR_slack_webhook_url` (marked sensitive). While unset, the secret holds the literal string `placeholder` and the Lambda no-ops, so the plumbing can apply before the webhook exists. Populate the workflow secret and reapply to switch delivery on.

Alert rules are stamped without an explicit `env`/`deployment`/`network`/`service` label — those come through from the underlying series' external_labels (set by the sidecar). Aggregating alerts (e.g. `sum by (...) rate(...)`) must include those labels in the `by` clause or Alertmanager `.CommonLabels` will drop them.

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

`aws_grafana_workspace_service_account_token.terraform.key` and `aws_secretsmanager_secret_version.amg_sa_token.secret_string` are both stored in plaintext in the terraform state file, per how the terraform state model works. This matches how every other secret this repo manages via terraform is stored (MongoDB Atlas passwords, WAF bypass tokens). The mitigation is at the state-backend layer: read access to `s3://veworld-indexer-terraform-state-prod` is scoped to the deploy OIDC role. Anyone with access to the bucket effectively has access to those secrets.

## Backend locking

Enabled via `use_lockfile = true` on the S3 backend (S3-native locking, no DynamoDB). Matches `terraform/api` and `terraform/vpc` after the repo-wide toolchain bump to terraform 1.13.5.
