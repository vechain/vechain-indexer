# Observability Terraform Stack

Amazon Managed Prometheus (AMP) + Amazon Managed Grafana (AMG) workspaces for the veworld-indexer observability migration. Phase 2 of the plan captured in `notes/observability-migration.md`.

## What this stack contains

- One AMP workspace with CloudWatch log group for ingestion logs.
- One AMG workspace at Grafana v12, `permission_type = CUSTOMER_MANAGED`, authenticated via SAML.
- Workspace IAM role that lets AMG read AMP and CloudWatch (metrics + logs) and manage silences.
- Terraform-provider service account with an admin token in Secrets Manager, rotated every 25 days.
- Okta SAML configuration — gated on `okta_saml_metadata_url` being non-empty. Empty by default until the Okta app is registered.

Deliberately not in this stack (yet): dashboards, alert rules, scrape targets. Those land in later phases as separate stacks that read the outputs here via `terraform_remote_state`.

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

`aws_grafana_workspace_service_account_token.terraform.key` and `aws_secretsmanager_secret_version.amg_sa_token.secret_string` are both stored in plaintext in the terraform state file, per how the terraform state model works. This matches how every other secret this repo manages via terraform is stored (MongoDB Atlas passwords, Datadog API keys, WAF bypass tokens). The mitigation is at the state-backend layer: read access to `s3://veworld-indexer-terraform-state-prod` is scoped to the deploy OIDC role. Anyone with access to the bucket effectively has access to those secrets.

## Backend locking

The S3 backend uses `use_lockfile = true` (native S3 conditional-write locking, GA in terraform 1.11). No DynamoDB table required. This is stricter than the sibling stacks in this repo (`terraform/api`, `terraform/vpc`) which currently rely on human coordination — worth aligning them in a follow-up.
