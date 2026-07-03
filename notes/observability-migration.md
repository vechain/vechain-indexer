# Observability Migration Plan

Migrate the veworld-indexer observability stack off Datadog onto AWS-hosted Prometheus (AMP) and Grafana (AMG). Primary goal: cost reduction. Secondary: add API-service metrics we currently lack, and simplify a stack whose surface area has grown organically.

Status: Phase 0 — this document. Subsequent phases are scoped below and will each land as their own PR.

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

- Metrics: Datadog agent sidecar on ECS tasks, `DD_HOST_TAG = "${env}-${network}"`. No metrics from the API service.
- Logs: three Datadog log pipelines (app, WAF, general) plus CloudWatch metric filters defined per-service in terraform.
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

Grafana dashboards template on `$env`, `$deployment`, `$network` so a single dashboard covers all four permutations and the blue/green switcher is a dropdown.

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

### P0 — audit and cull (this doc + follow-up)

- Commit this plan.
- Produce a keep/kill list from `metrics/datadog/dashboard.json`, the DD monitors, `terraform/api/cwalarms.tf`, and the log pipelines. Anything nobody has looked at or that has not paged should not be migrated.
- Confirm the final answers on open decisions (see below).

### P1 — AMP + AMG workspaces (shared, single apply)

- New terraform module for AMP workspace, AMG workspace with `CUSTOMER_MANAGED` role, log group for AMP ingestion, rotating service-account token stored in Secrets Manager.
- Okta SAML: gated on the metadata URL being populated. Ships wired but disabled if the Okta app isn't ready yet.
- No dashboards, no scrape targets. Empty workspaces.
- Rollback: `terraform destroy` on the module. Nothing else references it yet.

### P2 — indexer sidecar (per-colour rollout)

- New terraform module for the ADOT sidecar container definition and the IAM policy granting `aps:RemoteWrite` on the AMP workspace.
- Attach to indexer task on the dead colour.
- Verify series arrive in AMP with the correct labels via a scratch Grafana panel. Observe for one warm-up window.
- DNS switch. Apply sidecar terraform to the now-dead colour.
- Rollback: revert task definition on the affected colour.

### P3 — API instrumentation + sidecar

- Add `micrometer-registry-prometheus` to `packages/api/build.gradle.kts`.
- Enable `management.endpoints.web.exposure.include=health,prometheus` and configure common tags (`service=api`, others via sidecar labels).
- Ship a tight starter metric set: RED per endpoint, JVM basics, MongoDB driver pool. Nothing custom yet.
- Attach the sidecar to the API task via the same per-colour rollout as P2.
- Rollback: revert image + task definition on the affected colour.

### P4 — core dashboards as code

- Grafana provider provisions dashboards from JSON checked in under `metrics/grafana-amg/` (path TBD).
- Target ~6–10 dashboards, one per audience: indexer sync, indexer per-task health, API RED, MongoDB, JVM, business KPIs.
- All dashboards templated on `$env`, `$deployment`, `$network`.
- Rollback: remove dashboard files, re-apply.

### P5 — alarms migration

- Port the paging alarms first. `terraform/api/cwalarms.tf` stays until each alarm is replaced upstream.
- AMP recording/alerting rules for anything metric-based; Grafana Alerting for anything log-based or multi-source.
- Route to the same paging sinks Datadog uses today.
- Rollback: leave the CloudWatch alarms in place until each replacement has fired at least once in anger.

### P6 — logs

- Keep logs in CloudWatch. Add Grafana's CloudWatch Logs datasource.
- Retire log-based metric filters wherever an equivalent Micrometer counter now exists (from P3).
- Retire the Datadog log pipelines.
- Rollback: log pipelines are declarative; re-apply the previous state.

### P7 — remove Datadog

- Rip out DD env vars, secrets, terraform data sources, `DD_*` references in application code.
- Delete `metrics/datadog/`, `metrics/grafana/`, `metrics/prometheus/`, `metrics/compose.yaml`.
- Remove `make dd-refresh-generated` and the CI checks that depend on it.
- Update `CLAUDE.md` and `AGENTS.md` to strike the Datadog sections.
- Rollback: revert the PR. Datadog account and secrets stay unchanged until this ships successfully.

## Open decisions

Captured here so we don't lose them; each will be resolved before the phase it blocks.

1. **Log backend.** Recommended: CloudWatch Logs Insights via AMG's datasource. No new component. Loki stays out of scope unless we hit query pain that Insights cannot solve.
2. **Okta SAML for AMG.** Ship AMG with SAML gated off (empty `okta_saml_metadata_url`) if the Okta app isn't ready — service-account token still works for terraform provisioning. Flip on once the Okta app is registered.
3. **P0 audit ownership.** Requires Datadog UI access to see what's actually looked at and what has fired. Needs a human with the DD console. Propose: keep/kill list drafted from the checked-in JSON + `cwalarms.tf`, validated against DD by whoever has access.
4. **Sidecar sizing.** Start with a soft memory reservation of 128 MiB, revisit once real cardinality is known. If AMP series counts blow past expected budgets, sample or drop before scaling the sidecar.
5. **AMP retention.** AMP caps at 150 days. If any current DD retention exceeds that (unlikely for anything actionable), we need an S3 snapshot side-channel. Assume no for now.

## Risks

- **Cardinality blow-up.** Adding `task_id` as a label multiplies series count by the number of tasks over time. Micrometer default tags on HTTP endpoints can also explode if URIs are not templated. Mitigation: strict allowlist of metric tags in the API config, review AMP active-series count after P3 and again after P4.
- **First-time AMP/AMG creation is not covered by blue/green canary.** Mitigation: empty-workspace creation is low-risk; verify with terraform plan review before P1 apply.
- **Alarm gaps during migration.** For each paging alarm being ported, keep the CloudWatch/DD source of truth live until the replacement has demonstrably fired for a real condition. No silent windows.
- **CloudFront / WAF changes remain uncovered.** Unchanged from today. Out of scope; called out for clarity.
- **Cost during dual-run.** Blue/green canary means only the dead colour dual-emits, so the DD bill increment is bounded. We do not run both metric backends on live traffic for long.
