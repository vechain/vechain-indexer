# observability-grafana

Terraform stack that provisions AMG data sources and (eventually) dashboards, on top of the AMP + AMG workspaces created by `terraform/observability/`.

## What this stack contains

- **AMP data source** — Prometheus with SigV4 auth against the workspace in `terraform/observability/`. UID `amp`.
- **CloudWatch data source** — for log-based diagnostics and any CW-native metrics we keep. UID `cloudwatch`.
- **Dashboards** — JSON files under `dashboards/`, iterated by `dashboards.tf` via `for_each`. `overview` covers indexer sync, API, errors, ECS resources, MongoDB and the CloudFront/WAF edge row; `logs` is a single Logs Insights view over every ECS service log group.

## Adding a dashboard

1. Drop the JSON file under `dashboards/`. Author it in Grafana UI, then export via *Share → Export → Save to file*. Or hand-write from an existing file — the shape is small.
2. Reference the AMP data source by UID `amp` (or `cloudwatch` for CW). No terraform templating in the JSON — the `dashboards.tf` uses `file()`, not `templatefile()`, so JSON is portable and can be re-imported into Grafana for iteration.
3. Add an entry to `local.dashboards` in `dashboards.tf`. The key is the terraform resource key; the dashboard's `uid` field (in the JSON) controls the URL slug.

### Template variables convention

Dashboards share `deployment` (`blue`/`green`) and `network`, both multi-select with `All`, and filter with `deployment=~"$deployment", network=~"$network"`. `env` is not templated — the AMP workspace only holds `prod`.

Invariants worth knowing before editing:

- **`network` values differ by datasource.** Metrics carry `mainnet`/`testnet` (the `network_label` map in `terraform/api/observability.tf`, emitted as an external label by the sidecar); log group names use `main`/`test`. `overview` uses the former, `logs` the latter. That is why the Logs dashboard link sets `includeVars: false` — carrying the value across would silently match nothing.
- Both are `custom`, not `label_values`. Query-variable options vanish when the backing series briefly disappears (no testnet `indexer_current_block` mid-deploy), taking dependent panels with them. The trade is that the values are now hardcoded, so they must track the sidecar's external labels.
- `network`'s `allValue` must be an explicit alternation (`mainnet|testnet`), not `.*` — the Edge mapping variables interpolate it into a regex.
- Prometheus-side ad-hoc filtering goes through the `filters` variable, not per-label textboxes. Ad-hoc filters only apply to their own datasource, so they never reach the CloudWatch panels.

### Edge row (CloudFront + WAF)

CloudFront and WAF aren't in this repo's terraform, and their CloudWatch dimensions (`DistributionId`, `WebACL`) can't be derived from `$network` — the mainnet WebACL carries no network token. Hidden `cf_distribution` / `waf_acl` variables hold `network:resource` pairs and capture the resource via `regex: /^(?:${network:pipe}):(.*)$/`.

- Leave their `allValue` unset and `current` on `$__all`. Pinning specific values means a network switch falls back to the first option, silently dropping the second distribution.
- Edge panels are not blue/green scoped; `$deployment` has no effect on them.
- Keep the row collapsed — collapsed rows don't execute their queries.

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
