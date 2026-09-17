# observability-grafana

Terraform stack that provisions AMG data sources and (eventually) dashboards, on top of the AMP + AMG workspaces created by `terraform/observability/`.

## What this stack contains

- **AMP data source** — Prometheus with SigV4 auth against the workspace in `terraform/observability/`. UID `amp`.
- **CloudWatch data source** — for log-based diagnostics and any CW-native metrics we keep. UID `cloudwatch`.
- **Dashboards** — JSON files under `dashboards/`, iterated by `dashboards.tf` via `for_each`. `overview` covers indexer sync, API, errors, ECS resources, MongoDB, the CloudFront/WAF edge rows and the per-network WAF request detail rows; `logs` is a single Logs Insights view over every ECS service log group; `backups` covers the Postgres RDS snapshots.

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

### Backups dashboard

Reads the `VeWorld/RDSBackups` metrics published by the inventory Lambda in
`terraform/observability/backups.tf`, alongside the three `AWS/RDS` backup-storage metrics. The
"Backup inventory" section of that stack's README is where the metrics and their caveats are
documented — in particular why no panel here shows a per-snapshot size.

- **No template variables, on purpose.** Custom-namespace panels take `DBInstanceIdentifier: ["*"]`,
  which is already scoped to instances carrying the `Backup` tag. The `AWS/RDS` panels hardcode the
  four identifiers instead, because a wildcard there would pull in every RDS instance in the
  account. Four series read fine on one panel, and the Edge rows above are the cautionary tale
  about templating CloudWatch dimensions.
- **"Snapshots that exist" collapses repeats with `stats … by instance, snapshot`.** The Lambda
  logs the whole list every 5 minutes, so without the `stats` the table would show each snapshot
  once per poll in the range. The consequence is that the table is scoped to the dashboard's time
  range: a snapshot that aged out of retention leaves the table once the range moves past its last
  sighting, and widening the range is how you see what existed at some point in the past.
- **`parse` uses a regex, not a glob.** The Lambda's line is pipe-delimited, and a regex is
  unanchored for certain — a glob pattern would depend on whether the Lambda runtime prefixes the
  line, which differs by log format.
- **Adding a Postgres instance means editing the hardcoded identifier lists** in the two `AWS/RDS`
  panels. The custom-namespace panels pick it up on their own.


### Sync row

Both bar gauges carry the indexer's mode in the bar label rather than devoting a panel to it. The
mode comes from the `indexer_sync_status` one-hot, joined in with
`* on (deployment, network, indexer_name) group_left(status) (... == 1)` — multiplying by the
selected 1 leaves the value alone and carries the `status` label across. It is wrapped in
`last_over_time(...[45s])` for the same reason the pie chart is: remote write carries no staleness
markers, so without it a replaced task's final status wins the `max` for the full 5m lookback.

Labels are shortened by `label_replace` because 28 indexers × colour × network does not fit a bar:
the trailing `Indexer` comes off the name, the colour is cut to its initial, `-net` is dropped from
the network, and each status maps to a short form. The status rules all target lowercase, so no rule
can re-match a value an earlier one already rewrote — `label_replace` regexes are fully anchored,
which is what makes the chain order-independent.

"Time to fully synced" divides the sync gap by the rate the gap is closing —
`rate(indexer_blocks_processed_total[30m])` less the chain's own growth,
`deriv(thor_best_block_number[30m])`. Things worth knowing before trusting a number on it:

- **It is a rate extrapolation, not a schedule.** `IndexerRunner` batches indexers into proximity
  groups and alternates them against catch-up slices, so an indexer idling behind its group records
  no throughput and reads as `> 7d` until its turn comes round.
- **Fast sync and the live loop are different regimes.** A `fast` indexer runs an order of magnitude
  faster, so its estimate jumps the moment it reaches `ready`. The mode in the bar label is what
  tells you a jump was a phase change rather than a stall clearing.
- **The 30m windows are the floor, not a default.** Log indexers process in adaptive ranges; the 1m
  window "Blocks / sec" uses is far too twitchy to divide by.
- The denominator is clamped at 0.001 blocks/s so a stalled indexer lands past the `> 7d` mapping
  instead of dividing by zero, and `FULLY_SYNCED` is pinned to 0 the same way the gap panel pins it.

The `indexer_name` variable reads `indexer_sync_status`, not `indexer_current_block`: the latter is
only emitted once an indexer is past `NOT_INITIALISED`, so sourcing the picker from it hid exactly
the indexers you open this row to find.

### Edge rows (CloudFront + WAF)

One collapsed row per network, with the distribution ids and WebACL names hardcoded. Neither picker applies to them: CloudFront and WAF are shared across blue/green, and their CloudWatch dimensions can't be templated off `$network` — the ids are opaque and the mainnet WebACL carries no network token, so nothing is derivable.

- **Don't try to drive these from `$network` with a hidden mapping variable.** Options of `network:resource` pairs filtered by `regex: /^(?:${network:pipe}):(.*)$/` looks right and deploys clean, but Grafana does not resolve it for `custom` variables — the panels silently get the unstripped `mainnet:E15Q…` as the dimension and return no data. Per-network rows are the working arrangement.
- **The two stat tiles collapse both distributions with transformations, not metric math.** A stat panel reduces per series, so the CloudWatch response is joined on time and summed/averaged per timestamp (`joinByField` + `calculateField` in `reduceRow` mode) before the panel's own reducer runs. `m1+m2` metric math would look tidier and silently return nothing whenever the spare distribution has no datapoints, since CloudWatch math only emits where every operand has one.
- **Both tiles leave `period` on auto, unlike the graphs beside them.** CloudFront keeps 1-minute metrics for 15 days, so a hardcoded `60` returns an empty range past that; auto rolls up instead. A `Sum` totals the same either way, and "Cache hit rate (average)" is an unweighted mean of CloudFront's per-period averages — CloudFront publishes no hit/miss counts, so no request-weighted ratio is available from these metrics. Quiet minutes weigh as much as busy ones; read the number with the graph.
- Keep both rows collapsed — collapsed rows don't execute their queries, so the CloudWatch calls only happen when someone opens a row.
- Adding a network means duplicating a row. That duplication is deliberate, and cheaper than the alternative.
- "WAF — counted requests by rule (dry run)" is mainnet-only and reads `CountedRequests`, not `BlockedRequests`. A COUNT rule lets the request through, so the panel measures what a tighter limit *would* catch. `waf--custom-AWS-count-high-rate-ip` carries no exemptions, so the block-explorer proxy egress is counted alongside everything else — read the per-IP detail before attributing a spike.
- **An unmatched rule has no series, not a zero one.** CloudWatch creates a metric on its first datapoint, so while no IP crosses `high_rate_count_limit` the dry-run rule is missing from the legend rather than drawn along zero — and a lone missing series is indistinguishable from a broken query. The WebACL-wide `Rule=ALL` series is on the panel to settle that: it is populated by the managed count-mode rules, so if it draws and the dry-run series does not, the query path is fine and the answer is genuinely nothing. The two are not stacked, since the dry-run rule is a subset of `ALL`.
- **Use `wafv2 get-sampled-requests` for the flagged IPs, not `get-rate-based-statement-managed-keys`.** The latter lists keys actually being limited, so a count-only rule leaves it empty even while the metric climbs — it returned nothing during a bucket that counted 883 requests. Sampled requests carry client IP, country, URI and headers for the last three hours, which is what identified the rule's first real catches: Googlebot, a residential viewer on the homepage cards, and the explorer's own CSV export paging one address. All three were legitimate, which is why the threshold sits above them rather than at the tightest value that fires.

### WAF request detail rows (Logs Insights)

One collapsed row per network, over `aws-waf-logs-veworld-cloudfront` and `aws-waf-logs-veworld-testnet-cloudfront`. Both live in **us-east-1** — CLOUDFRONT-scope ACLs log there, not in the stack's own region — so the targets set `region` explicitly rather than `default`.

- Logs Insights bills per byte scanned, and the mainnet group is the largest log group in the account. Keep the rows collapsed; that is what stops the queries running on every dashboard load.
- **The two groups do not hold the same thing.** Mainnet runs `logging_filter_block_only`, so it carries BLOCKed requests only — plus COUNT-matched ones while `logging_filter_include_count` is on, which is how the count rule's client IPs reach "Flagged clients". That flag logs every request from a flagged IP, so it belongs on for a diagnostic window and off the rest of the time; `get-sampled-requests` gives the offending IPs for free if the volume does not justify it. Testnet has no filter and carries every inspected request. Every query therefore opens with `filter terminatingRuleId != "Default_Action" or ispresent(nonTerminatingMatchingRules.0.ruleId)` — a no-op on mainnet, the whole point on testnet, and what makes "flagged" mean the same on both.
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
