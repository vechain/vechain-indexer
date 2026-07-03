# observability-grafana

Terraform stack that provisions AMG data sources and (eventually) dashboards, on top of the AMP + AMG workspaces created by `terraform/observability/`.

## What this stack contains

- **AMP data source** — Prometheus with SigV4 auth against the workspace in `terraform/observability/`.
- **CloudWatch data source** — for log-based diagnostics and any CW-native metrics we keep.

Dashboards land in follow-up PRs.

## Usage

```bash
cd terraform/observability-grafana
terraform init -backend-config=environments/prod.config
terraform workspace select -or-create prod
terraform plan
terraform apply
```

## Provider auth

The Grafana provider authenticates against the AMG workspace using the service-account token minted by `terraform/observability/`. That token is written to Secrets Manager and its ARN is exposed as a `terraform_remote_state` output.

The token has a 30-day TTL and is rotated every 25 days by `time_rotating` in the parent stack — so this stack must be `terraform apply`'d again within 30 days of every parent apply, or the SA token in state goes stale and the provider fails to authenticate. See the "Operational notes" section of `terraform/observability/README.md` for the workflow story.
