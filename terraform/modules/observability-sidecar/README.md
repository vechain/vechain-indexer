# observability-sidecar

Local terraform module that produces the container definition and IAM statement needed to attach an ADOT (AWS Distro for OpenTelemetry Collector) sidecar to an ECS task, scraping the app's `/actuator/prometheus` endpoint and remote-writing to Amazon Managed Prometheus.

Two receivers feed the pipeline: the app's Prometheus endpoint (application metrics) and `awsecscontainermetrics` (per-task and per-container cgroup CPU/memory, `ecs_task_*` and `container_*` series).

Consumed by the same-account observability workspace created in `terraform/observability/`. Phase 4 of the migration plan attaches this to the indexer task; phase 5 attaches it to the API task.

## Outputs

- `container_definition` — pass into `additional_containers` on the `ecs-backend-service` (or `ecs-loadbalanced-webservice`) module. Sidecar is `essential=false`, so a sidecar crash cannot restart the app.
- `amp_remote_write_statement` — pass into `extra_statements` on the same module. Grants `aps:RemoteWrite` scoped to the AMP workspace ARN only.

## Labels

Every metric coming out of the sidecar carries:

| Label        | Source                              |
| ------------ | ----------------------------------- |
| `service`    | module input, static                |
| `env`        | module input, static                |
| `deployment` | module input, static (`blue`/`green`) |
| `network`    | module input, static (`mainnet`/`testnet`) |
| `task_id`    | ADOT resourcedetection (ECS)        |
| `container`  | ADOT resourcedetection (ECS)        |

The app itself must not emit `service` / `env` / `deployment` / `network` — the sidecar is the single source of truth for these dimensions.

## Rollout

Per the migration plan, the sidecar is enabled per environment via a gate in `terraform/api/environments/<env>.yml`. Attach it to the dead colour first, watch the labels arrive in AMP for a warm-up window, DNS-switch, then apply to the now-dead colour.
