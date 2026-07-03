# observability-grafana

Terraform stack that provisions AMG data sources and (eventually) dashboards, on top of the AMP + AMG workspaces created by `terraform/observability/`.

## What this stack contains

- **AMP data source** — Prometheus with SigV4 auth against the workspace in `terraform/observability/`.
- **CloudWatch data source** — for log-based diagnostics and any CW-native metrics we keep.

Dashboards land in follow-up PRs.

## Bootstrap (one-time, local)

The CI workflow uses `terraform workspace select prod` (without `-or-create`) so PR plans can't silently create the prod workspace in S3. First-time setup is a manual local step:

```bash
cd terraform/observability-grafana
terraform init -backend-config=environments/prod.config
terraform workspace new prod
```

## Usage

After bootstrap, subsequent plans/applies happen through the `Plan or Apply Observability Terraform` workflow. Local use:

```bash
cd terraform/observability-grafana
terraform init -backend-config=environments/prod.config
terraform workspace select prod
terraform plan
terraform apply
```

## Provider auth

The Grafana provider authenticates against the AMG workspace using the service-account token minted by `terraform/observability/`. That token is written to Secrets Manager and its ARN is exposed as a `terraform_remote_state` output. This stack reads the current secret value at plan/apply time via `aws_secretsmanager_secret_version`, so it always sees whatever the parent last wrote.

The token has a 30-day TTL and is rotated by `time_rotating` in the parent stack. If the parent isn't `terraform apply`'d within 30 days, the token in Secrets Manager expires and the Grafana provider here will fail to authenticate on the next apply. See the "Operational notes" section of `terraform/observability/README.md` for the workflow story.

The SA token materialises in this stack's terraform state as well (the `aws_secretsmanager_secret_version` data source), on top of the copies in the parent stack's state and Secrets Manager itself. Same mitigation applies — restrict read access to `s3://veworld-indexer-terraform-state-prod` to the deploy OIDC role.
