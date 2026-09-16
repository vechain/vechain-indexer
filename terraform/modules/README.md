# Shared Terraform modules

`observability-sidecar` is ours. The rest were vendored from the shared module
repositories so that `terraform init` needs no private git access and a bug in a module
can be fixed in the PR that finds it, rather than in another repository and a version
bump.

| Module | Vendored from | Ref |
| --- | --- | --- |
| `ecr` | `vechain/terraform_infrastructure_modules//ecr` | `v.3.1.21` |
| `ecs_cluster` | `vechain/terraform_infrastructure_modules//ecs_cluster` | `v.3.1.8` |
| `ecs-backend-service` | `vechain/terraform_infrastructure_modules//ecs-backend-service` | `v.3.2.1` |
| `vpcendpoint` | `vechain/terraform_infrastructure_modules//vpcendpoint` | `v.1.0.19` |
| `cloudwatchalarm` | `vechainfoundation/terraform_infrastructure_modules//cloudwatchalarm` | `v.1.0.2` |
| `cloudwatchdashboard` | `vechainfoundation/terraform_infrastructure_modules//cloudwatchdashboard` | `v.1.0.2` |
| `logs` | `vechainfoundation/terraform_infrastructure_modules//logs` | `v.1.0.2` |

These are now the source of truth. Edit them here; there is no upstream to sync back to.
Each module's own `README.md`, where it has one, is the upstream copy and may still
describe a `git::` source.

## Changes made on vendoring

- `ecs_cluster` and `ecs-backend-service` pointed at the upstream `ecr` module by git URL;
  both now use `../ecr`.
- `ecs-backend-service` assigned `var.healthcheck` straight to the container definition's
  `healthCheck`. The variable names the start period `start_delay`, ECS names it
  `startPeriod`, and AWS drops unknown keys without complaint, so no task ever had one —
  a container had three failed checks, about 90s, to finish booting. The field is now
  mapped explicitly. `api/modules/ecs-loadbalanced-webservice` had the same bug and the
  same fix, so the API tasks were affected too.
- `terraform fmt` over the vendored files.

## Known follow-ups

- Every workflow that runs Terraform still sets up an SSH agent with
  `VECHAINCI_SSH_PRIVATE_KEYS` for module access that no longer happens. Removing it
  touches the prod apply path, so it was left out of the vendoring change.
  `codebase-versioning.yml` needs that key for its own reasons and should keep it.
- The `logs` module uses `data.aws_region.current.name`, deprecated in favour of `region`.
  The same call appears in `observability`, `observability-grafana` and
  `api/modules/ecs-loadbalanced-webservice`; it wants one sweep rather than a partial fix.
