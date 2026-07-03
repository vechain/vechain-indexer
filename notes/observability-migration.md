# Observability Migration Plan

Migrate the VeWorld Indexer observability stack off Datadog onto AWS-hosted Prometheus (AMP) and Grafana (AMG). Primary goal: cost reduction. Secondary: add API-service metrics we currently lack, and simplify a stack whose surface area has grown organically.

Status: Phase 0 — this document. Phase 1 (cull) and everything after are scoped below and will each land as their own PR.

## Goals

- Remove Datadog dependency (agent, log pipelines, dashboards, monitors, secrets, terraform wiring).
- Introduce first-class metrics for the API service — currently we have none.
- Emit metrics tagged at task level so we can distinguish blue/green, mainnet/testnet, and individual ECS tasks.
- Consolidate ingestion sources. We currently ingest logs and metrics from more places than we look at.
- Keep the change reversible at each step. No big-bang cutover.

## Non-goals

- Feature parity with the existing Datadog dashboards. The 5,671-line `metrics/datadog/dashboard.json` is a floor to move away from, not a target to reproduce.
- Maintaining a parallel local Prometheus/Grafana stack. Dashboards will depend on labels (task id, deployment colour) that don't exist locally, so the local stack would drift into a lie.
- Adding a full dev environment. See "Testing strategy" below — we use the existing blue/green mechanism as a canary instead.
- Migrating CloudFront and WAF observability in this workstream. Both live outside our terraform today; changes there are out of scope until they are brought under IaC.

## Current state

- Metrics: only the indexer pushes to Datadog. Micrometer's Datadog registry runs inside the indexer JVM and posts to `api.datadoghq.eu` every 10s. No sidecar container, no DD agent. Driven by `management.datadog.metrics.export.enabled` in `packages/indexer/src/main/resources/application.yaml` and by `DD_METRICS_ENABLED` / `DD_API_KEY` / `DD_HOST_TAG` env vars set on the `ecs-backend-service` (indexer) module in `terraform/api/api.tf` (`DD_HOST_TAG = "${env}-${network}"`). The API task definition does not set these env vars, and the API's yaml pins the DD export to `false` — see the next bullet.
- Indexer already exposes `/actuator/prometheus` in parallel (`PROMETHEUS_METRICS_ENABLED` defaults to `true`). The endpoint is live today; nothing scrapes it in prod.
- API has no exported metrics: `management.datadog.metrics.export.enabled` is pinned false, `management.prometheus.metrics.export.enabled` is pinned false, and `management.endpoints.web.exposure.include` only lists `health`. The Micrometer libraries are on the classpath (added at root `build.gradle.kts`), so enabling metrics is a config change, not a dependency change.
- Logs: a Datadog Forwarder Lambda (deployed by a shared `datadog` terraform module as a CloudFormation stack) reads from CloudWatch log groups and forwards to DD. Log pipelines (app, WAF, general) and monitor definitions live inside Datadog itself; the JSON in `metrics/datadog/` is what's exported/replicated locally.
- Dashboards: one large Datadog dashboard checked in at `metrics/datadog/dashboard.json`.
- Local dev: `metrics/compose.yaml` spins up Prometheus + Grafana against the indexer's `/actuator/prometheus` endpoint. Not used by anyone in a while.
- CloudWatch: a hand-maintained dashboard and alarm set in `terraform/api/cloudwatch_dashboard.tf` and `terraform/api/cwalarms.tf`. These stay for now.

## Target architecture

- **Amazon Managed Prometheus** as the metrics backend. One workspace per environment (dev, prod). Remote-write from each ECS task via an ADOT sidecar.
- **Amazon Managed Grafana** as the query and dashboard front end, authenticated via Okta SAML. Dashboards provisioned as code through the Grafana terraform provider so they live in this repo.
- **ADOT sidecar container** attached to each ECS task. Scrapes `localhost:8080/actuator/prometheus` inside the task, enriches series with the ECS task id via the resourcedetection processor, and remote-writes to AMP with SigV4 signing.
- **Logs** stay in CloudWatch. Grafana queries them through the CloudWatch Logs datasource. No new log-storage component.
- **Alerts** run as AMP recording/alerting rules plus Grafana Alerting, wired to the existing paging channels.

## Labelling model

Every series carries the following external labels, either from the sidecar config (static) or from the resourcedetection processor (dynamic):

| Label        | Source          | Values                                  |
| ------------ | --------------- | --------------------------------------- |
| `env`        | sidecar static  | `dev`, `prod`                           |
| `deployment` | sidecar static  | `blue`, `green`                         |
| `network`    | sidecar static  | `mainnet`, `testnet`                    |
| `service`    | sidecar static  | `api`, `indexer`                        |
| `task_id`    | ECS detector    | ECS task ARN suffix                     |
| `container`  | ECS detector    | container name inside the task          |

Grafana dashboards are templated on `$env`, `$deployment`, `$network` so a single dashboard covers all four permutations and the blue/green switcher is a dropdown.

## Testing strategy — blue/green as canary

Deploying observability infra directly to live is risky. Standing up a full dev environment is expensive and doesn't cover the things we would actually worry about (CloudFront, WAF — neither in IaC).

We already have a blue/green setup where one colour is dead at any given time. Task-definition-scoped changes (like adding an ADOT sidecar) can land on the dead colour first, be observed for a warm-up window, then be exposed to traffic via the normal DNS switch. Rollback is "don't switch".

Applies to:
- Per-colour terraform changes: sidecar attachment, task-definition edits, per-colour env vars, per-colour IAM.
- Alarm scoping: initial alert rules are filtered by `deployment` label so dead-colour noise stays silent.

Does not apply to:
- Shared/global resources: AMP workspace, AMG workspace, IAM roles the sidecar assumes, CloudWatch log groups. These land once, up front. Blast radius of creating empty workspaces is low.
- Image-borne code changes such as the API Micrometer instrumentation. Those land on both colours together via the normal release. Blue/green isolates infra, not code.

Operational note: the dead colour must be warm during the observation window. If current practice is to scale dead to zero between deploys, we leave it running for the duration of a canary window.

## Phases

Each phase is one PR unless noted. Rollback path is called out where non-trivial.

### P0 — this plan doc

- Commit this plan. No infra or code changes.
- Confirm the final answers on open decisions (see below) as they get resolved.

### P1 — cull

Reduce the DD footprint before we start migrating. Deleting things costs less than porting them.

Keep (in scope for migration):

- Indexer Sync & Processing — actively used.
- API Performance — actively used.
- Thor Client Metrics — used recently for troubleshooting.
- Resource Utilization — important; we will extend this in later phases to cover the API service and pivot to task level.
- MongoDB Metrics — nice to have.
- JVM Metrics — nice to have.

Cull (delete from DD, do not migrate):

- Traffic & CDN section.
- Origin & Security section.
- Top-level widgets sitting outside any group: reviewed case-by-case in the PR — anything duplicating a kept panel or referencing a culled section is dropped.

DD monitors / alerts: leave in place for this cull PR. Each individual monitor gets a keep/migrate/drop decision in P7 (alarms migration) rather than up front. The one thing this PR must produce is a list of every DD monitor that references custom indexer metric names, so P3 (metrics cleanup) knows which monitors need updating when metrics are renamed.

Log pipelines: the checked-in JSON at `metrics/datadog/app-pipeline.json`, `metrics/datadog/pipeline.json`, and `metrics/datadog/waf-pipeline.json` gets audited for keep/drop; log-based metric filters in `terraform/api/cwalarms.tf` similarly. Culled pipelines are removed in this PR; the rest wait for P8 (logs).

Local dev stack: delete `metrics/compose.yaml`, `metrics/prometheus/`, and `metrics/grafana/`. The indexer's `/actuator/prometheus` endpoint stays live for anyone who wants to `curl` it directly; there is no reason to run a separate local Prometheus + Grafana pair to visualise it. Removing this now keeps the stack from drifting further out of sync while the migration is in flight.

Rollback: DD dashboard is exportable; culled sections and the local stack can both be restored from git history if we regret it.

### P2 — AMP + AMG workspaces (shared, single apply)

- New terraform module for AMP workspace, AMG workspace with `CUSTOMER_MANAGED` role, log group for AMP ingestion, rotating service-account token stored in Secrets Manager.
- Okta SAML: gated on the metadata URL being populated. Ships wired but disabled if the Okta app isn't ready yet.
- No dashboards, no scrape targets. Empty workspaces.
- Rollback: `terraform destroy` on the module. Nothing else references it yet.

### P3 — indexer metrics cleanup (code-only)

Fix the existing custom metrics in `packages/indexer/src/main/kotlin/org/vechain/indexer/config/metrics/` before we start scraping. Doing this before P4 means dashboards and alerts get built against clean names first time, and the AMP series count stays bounded from day one.

Concrete work:

- **Rename to Prometheus conventions.** Drop the `_gauge` suffix (`indexer_current_block_gauge` → `indexer_current_block`, `thor_best_block_number_gauge` → `thor_best_block_number`, etc.). `_total` is reserved for counters; `indexer_blocks_processed_total` already uses it correctly and stays.
- **Dedupe sync-status metrics.** Today emitted three ways: `indexer_sync_status_gauge` (one-hot per enum), `indexer_sync_status_code_gauge` (numeric code), plus a duplicate `status_readable` tag. Keep the one-hot as the canonical form; drop the code gauge and the readable tag.
- **Delete derivable gauges.** `indexer_blocks_per_second_gauge` is `rate(indexer_blocks_processed_total[1m])` — remove it and the per-tick computation in `IndexerMetricsReporter`. `indexer_sync_gap_gauge` is `thor_best_block_number - indexer_current_block` — remove it, keep the two source gauges.
- **Add histogram buckets to timers.** `ProcessorMetrics` and `ThorClientMetrics` currently emit `Timer` without `publishPercentileHistogram(true)`, so p95/p99 in Grafana won't work. Enable histograms on the timers we care about.
- **Add a processor-level error counter.** `ThorClient` counts response codes, but processor-side failures only exist in logs. Add `indexer_processor_errors_total` tagged by indexer_name and error class.

DD compatibility: renames will break any DD dashboard panel or monitor that references old names. P1 cull output enumerates the DD monitors; the P3 PR updates any monitor that references a renamed metric so we don't lose alert coverage while DD is still authoritative. DD dashboard panels are allowed to break — we're retiring them.

Rollback: revert the PR. Metric names return to the current state.

### P4 — indexer sidecar (per-colour rollout)

- New terraform module for the ADOT sidecar container definition and the IAM policy granting `aps:RemoteWrite` on the AMP workspace.
- No app-side code work — the indexer already exposes `/actuator/prometheus`. The sidecar scrapes `localhost:8080/actuator/prometheus` and remote-writes to AMP with SigV4 signing.
- The existing in-process Micrometer Datadog push stays on for now. It gets turned off at the end of P5 once both services have proven the scrape path.
- Attach the sidecar to the indexer task on the dead colour.
- Verify series arrive in AMP with the correct labels via a scratch Grafana panel. Observe for one warm-up window.
- DNS switch. Apply sidecar terraform to the now-dead colour.
- Rollback: revert task definition on the affected colour.

### P5 — API instrumentation + sidecar

- Config-only change: flip `management.prometheus.metrics.export.enabled: true` and add `prometheus` to `management.endpoints.web.exposure.include`. The Micrometer Prometheus registry is already on the classpath.
- Do not emit `service`, `env`, `deployment`, or `network` as Micrometer common tags. These are infra-level labels and are owned by the sidecar exclusively; having Micrometer also set them invites drift and depends on the collector's `honor_labels` behaviour at remote-write time. Micrometer emits metric names + app-specific tags only (e.g. `endpoint`, `status`).
- Apply the same review discipline as P3 to the API starter set: don't emit anything computable in PromQL, use Prometheus naming conventions, keep tag cardinality tight (URI templating enforced on HTTP metrics).
- Starter set: HTTP RED, JVM basics, MongoDB driver pool. Custom API-side counters get added in follow-ups, not this PR.
- Attach the sidecar to the API task via the same per-colour rollout as P4.
- **End-of-phase step (rollout option B): turn off the in-process Micrometer Datadog push.** With both indexer and API scrape paths proven on both colours, drop `DD_METRICS_ENABLED=true` from terraform (making the yaml default of `false` win) and remove the associated `DD_*` env vars from the indexer task definition. DD still receives nothing else worth having at this point; the JVM push is the last live producer.
- Rollback: revert image + task definition on the affected colour; re-enable `DD_METRICS_ENABLED` if the JVM-push cutover step is what regressed.

### P6 — core dashboards as code

- Grafana provider provisions dashboards from JSON checked in under `metrics/grafana-amg/` (path TBD).
- Target ~6–10 dashboards, one per audience: indexer sync, indexer per-task health, API RED, MongoDB, JVM, business KPIs.
- All dashboards templated on `$env`, `$deployment`, `$network`.
- Rollback: remove dashboard files, re-apply.

### P7 — alarms migration

- Port the paging alarms first. `terraform/api/cwalarms.tf` stays until each alarm is replaced upstream.
- AMP recording/alerting rules for anything metric-based; Grafana Alerting for anything log-based or multi-source.
- Route to the same paging sinks Datadog uses today.
- Rollback: leave the CloudWatch alarms in place until each replacement has fired at least once in anger.

### P8 — logs

- Keep logs in CloudWatch. Add Grafana's CloudWatch Logs datasource.
- Retire log-based metric filters wherever an equivalent Micrometer counter now exists (from P5).
- Retire the Datadog log pipelines.
- Rollback: log pipelines are declarative; re-apply the previous state.

### P9 — remove Datadog

- JVM push is already off from end of P5. This phase completes the removal:
- Drop the `micrometer-registry-datadog` dependency from root `build.gradle.kts` and the `management.datadog` block from both `application.yaml` files.
- Rip out DD env vars, secrets, terraform data sources, DD Forwarder Lambda / CloudFormation stack, and any lingering `DD_*` references in application code.
- Delete `metrics/datadog/` (the local Prometheus + Grafana stack in `metrics/compose.yaml`, `metrics/prometheus/`, and `metrics/grafana/` is already gone from P1).
- Remove `make dd-refresh-generated` and the CI checks that depend on it.
- Update `CLAUDE.md` and `AGENTS.md` to strike the Datadog sections.
- Rollback: revert the PR. Datadog account and secrets stay unchanged until this ships successfully.

## Open decisions

Captured here so we don't lose them; each will be resolved before the phase it blocks.

1. **Log backend.** Recommended: CloudWatch Logs Insights via AMG's datasource. No new component. Loki stays out of scope unless we hit query pain that Insights cannot solve.
2. **Okta SAML for AMG.** Ship AMG with SAML gated off (empty `okta_saml_metadata_url`) if the Okta app isn't ready — service-account token still works for terraform provisioning. Flip on once the Okta app is registered.
3. **P1 cull ownership.** Requires Datadog UI access to execute the cull and enumerate monitors referencing custom metrics. Needs a human with the DD console. Propose: keep/kill list drafted from the checked-in JSON + `cwalarms.tf`, validated against DD by whoever has access.
4. **Sidecar sizing.** Start with a soft memory reservation of 128 MiB, revisit once real cardinality is known. If AMP series counts blow past expected budgets, sample or drop before scaling the sidecar.
5. **AMP retention.** AMP caps at 150 days. If any current DD retention exceeds that (unlikely for anything actionable), we need an S3 snapshot side-channel. Assume no for now.

## Risks

- **Cardinality blow-up.** Adding `task_id` as a label multiplies series count by the number of tasks over time. Micrometer default tags on HTTP endpoints can also explode if URIs are not templated. Mitigation: strict allowlist of metric tags in the API config, review AMP active-series count after P4 and again after P5.
- **First-time AMP/AMG creation is not covered by blue/green canary.** Mitigation: empty-workspace creation is low-risk; verify with terraform plan review before P2 apply.
- **Alarm gaps during migration.** For each paging alarm being ported, keep the CloudWatch/DD source of truth live until the replacement has demonstrably fired for a real condition. No silent windows.
- **CloudFront / WAF changes remain uncovered.** Unchanged from today. Out of scope; called out for clarity.
- **Cost during dual-run.** Blue/green canary means only the dead colour dual-emits, so the DD bill increment is bounded. We do not run both metric backends on live traffic for long.
