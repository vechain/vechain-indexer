# observability-grafana

Terraform stack that provisions AMG data sources and (eventually) dashboards, on top of the AMP + AMG workspaces created by `terraform/observability/`.

## What this stack contains

- **AMP data source** — Prometheus with SigV4 auth against the workspace in `terraform/observability/`. UID `amp`.
- **CloudWatch data source** — for log-based diagnostics and any CW-native metrics we keep. UID `cloudwatch`.
- **Dashboards** — JSON files under `dashboards/`, iterated by `dashboards.tf` via `for_each`. `overview` covers indexer sync, API, errors, ECS resources, MongoDB, the CloudFront/WAF edge rows and the per-network WAF request detail rows; `logs` is a single Logs Insights view over every ECS service log group.

## Adding a dashboard

1. Drop the JSON file under `dashboards/`. Author it in Grafana UI, then export via *Share → Export → Save to file*. Or hand-write from an existing file — the shape is small.
2. Reference the AMP data source by UID `amp` (or `cloudwatch` for CW). No terraform templating in the JSON — the `dashboards.tf` uses `file()`, not `templatefile()`, so JSON is portable and can be re-imported into Grafana for iteration.
3. Add an entry to `local.dashboards` in `dashboards.tf`. The key is the terraform resource key; the dashboard's `uid` field (in the JSON) controls the URL slug.

### Template variables convention

Dashboards share `deployment` (`blue`/`green`) and `network`, both multi-select with `All`, and filter with `deployment=~"$deployment", network=~"$network"`. `env` is not templated — the AMP workspace only holds `prod`.

Invariants worth knowing before editing:

- **`network` values differ by datasource.** Metrics carry `mainnet`/`testnet` (the `network_label` map in `terraform/api/observability.tf`, emitted as an external label by the sidecar); log group names use `main`/`test`. `overview` uses the former, `logs` the latter. That is why the Logs dashboard link sets `includeVars: false` — carrying the value across would silently match nothing.
- Both are `custom`, not `label_values`. Query-variable options vanish when the backing series briefly disappears (no testnet `indexer_current_block` mid-deploy), taking dependent panels with them. The trade is that the values are now hardcoded, so they must track the sidecar's external labels.
- Prometheus-side ad-hoc filtering goes through the `filters` variable, not per-label textboxes. Ad-hoc filters only apply to their own datasource, so they never reach the CloudWatch panels.

### Edge rows (CloudFront + WAF)

One collapsed row per network, with the distribution ids and WebACL names hardcoded. Neither picker applies to them: CloudFront and WAF are shared across blue/green, and their CloudWatch dimensions can't be templated off `$network` — the ids are opaque and the mainnet WebACL carries no network token, so nothing is derivable.

- **Don't try to drive these from `$network` with a hidden mapping variable.** Options of `network:resource` pairs filtered by `regex: /^(?:${network:pipe}):(.*)$/` looks right and deploys clean, but Grafana does not resolve it for `custom` variables — the panels silently get the unstripped `mainnet:E15Q…` as the dimension and return no data. Per-network rows are the working arrangement.
- Keep both rows collapsed — collapsed rows don't execute their queries, so the CloudWatch calls only happen when someone opens a row.
- Adding a network means duplicating a row. That duplication is deliberate, and cheaper than the alternative.

### WAF request detail rows (Logs Insights)

One collapsed row per network, over `aws-waf-logs-veworld-cloudfront` and `aws-waf-logs-veworld-testnet-cloudfront`. Both live in **us-east-1** — CLOUDFRONT-scope ACLs log there, not in the stack's own region — so the targets set `region` explicitly rather than `default`.

- Logs Insights bills per byte scanned, and the mainnet group is the largest log group in the account. Keep the rows collapsed; that is what stops the queries running on every dashboard load.
- **The two groups do not hold the same thing.** Mainnet runs `logging_filter_block_only`, so it carries BLOCKed requests only. Testnet has no filter and carries every inspected request. Every query therefore opens with `filter terminatingRuleId != "Default_Action" or ispresent(nonTerminatingMatchingRules.0.ruleId)` — a no-op on mainnet, the whole point on testnet, and what makes "flagged" mean the same on both.
- The `rule` column coalesces to `terminatingRuleId` before `labels.0.name` so it reads as the WebACL rule name (`waf--managed-AWS-…`), matching the `Rule` dimension in the Edge rows' "WAF — blocked requests by rule". Preferring the label instead yields `awswaf:managed:aws:…`, which cross-references nothing.

### ECS service task counts

"API tasks running against desired" in the Resources row is CloudWatch, not AMP, and its cluster/service dimensions are hardcoded for both colours and both networks. Two reasons it cannot be AMP: `ECS/ContainerInsights` service-level counts have no AMP equivalent (the ADOT sidecar emits per-task metrics only), and a task that dies takes its own series with it, so an AMP-derived count goes absent rather than dropping to zero. Same reason `terraform/api/alarms.tf` reads these metrics directly.

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
