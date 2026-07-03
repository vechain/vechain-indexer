# observability-grafana

Terraform stack that provisions AMG data sources and (eventually) dashboards, on top of the AMP + AMG workspaces created by `terraform/observability/`.

## What this stack contains

- **AMP data source** — Prometheus with SigV4 auth against the workspace in `terraform/observability/`. UID `amp`.
- **CloudWatch data source** — for log-based diagnostics and any CW-native metrics we keep. UID `cloudwatch`.
- **Dashboards** — JSON files under `dashboards/`, iterated by `dashboards.tf` via `for_each`.

## Adding a dashboard

1. Drop the JSON file under `dashboards/`. Author it in Grafana UI, then export via *Share → Export → Save to file*. Or hand-write from an existing file — the shape is small.
2. Reference the AMP data source by UID `amp` (or `cloudwatch` for CW). No terraform templating in the JSON — the `dashboards.tf` uses `file()`, not `templatefile()`, so JSON is portable and can be re-imported into Grafana for iteration.
3. Add an entry to `local.dashboards` in `dashboards.tf`. The key is the terraform resource key; the dashboard's `uid` field (in the JSON) controls the URL slug.

### Template variables convention

All dashboards use the same top-level variables so panels can be reused between them:

- `deployment` — multi-select with `All` (`blue` / `green`).
- `network` — multi-select with `All` (`mainnet` / `testnet`).

Panels filter with `deployment=~"$deployment", network=~"$network"` so the same expression works whether one or many values are selected. `env` is not templated — the AMP workspace only holds `prod`, so a picker with a single option adds noise.

## Usage

Plans/applies happen through the `Plan or Apply Observability Terraform` workflow. Local use:

```bash
cd terraform/observability-grafana
terraform init -backend-config=environments/prod.config
terraform workspace select -or-create prod
terraform plan
terraform apply
```

## Provider auth

The Grafana provider authenticates against the AMG workspace using the service-account token minted by `terraform/observability/`. That token is written to Secrets Manager and its ARN is exposed as a `terraform_remote_state` output. This stack reads the current secret value at plan/apply time via `aws_secretsmanager_secret_version`, so it always sees whatever the parent last wrote.

The token has a 30-day TTL and is rotated by `time_rotating` in the parent stack. If the parent isn't `terraform apply`'d within 30 days, the token in Secrets Manager expires and the Grafana provider here will fail to authenticate on the next apply. See the "Operational notes" section of `terraform/observability/README.md` for the workflow story.

The SA token materialises in this stack's terraform state as well (the `aws_secretsmanager_secret_version` data source), on top of the copies in the parent stack's state and Secrets Manager itself. Same mitigation applies — restrict read access to `s3://veworld-indexer-terraform-state-prod` to the deploy OIDC role.
