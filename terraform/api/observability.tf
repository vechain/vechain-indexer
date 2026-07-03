locals {
  observability_env             = split("-", local.env.environment)[0]
  observability_deployment      = split("-", local.env.environment)[1]
  observability_sidecar_enabled = try(local.env.observability_sidecar_enabled, false)
}

module "observability_sidecar_indexer" {
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
