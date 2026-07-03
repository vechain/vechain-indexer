# ADOT metrics sidecar wiring for the indexer task. Consumed by the
# `module.ecs-backend-service` block in api.tf via its additional_containers
# and extra_statements inputs.
#
# Phase 4 of notes/observability-migration.md: per-colour rollout — the
# sidecar is attached to the dead colour first, observed, then rolled to
# the (now-dead) previously-live colour after DNS switch. Gated per
# environment by `observability_sidecar_enabled` in
# environments/<workspace>.yml; default false.

locals {
  # `local.env.environment` is `prod-blue` or `prod-green`. The sidecar
  # emits `env` = `prod` (matches the observability workspace's env) and
  # `deployment` = `blue`|`green` (from the second segment). Both are
  # static external labels on every metric emitted by the sidecar.
  observability_env        = split("-", local.env.environment)[0]
  observability_deployment = split("-", local.env.environment)[1]

  observability_sidecar_enabled = try(local.env.observability_sidecar_enabled, false)
}

module "observability_sidecar_indexer" {
  # The sidecar is a per-network attachment because each network runs
  # its own indexer ECS task. Enabled/disabled together per colour via
  # the env-level flag above.
  for_each = local.observability_sidecar_enabled ? local.env.enabled_nets : {}
  source   = "../modules/observability-sidecar"

  service_name      = "indexer"
  env               = local.observability_env
  deployment        = local.observability_deployment
  network           = each.key == "main" ? "mainnet" : "testnet"
  aws_region        = local.env.region
  amp_endpoint      = data.terraform_remote_state.observability.outputs.amp_endpoint
  amp_workspace_arn = data.terraform_remote_state.observability.outputs.amp_workspace_arn
  app_port          = 8080
}
