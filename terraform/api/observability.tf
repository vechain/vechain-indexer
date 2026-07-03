locals {
  observability_env             = split("-", local.env.environment)[0]
  observability_deployment      = split("-", local.env.environment)[1]
  observability_sidecar_enabled = try(local.env.observability_sidecar_enabled, false)

  network_label = {
    main = "mainnet"
    test = "testnet"
  }
}

module "observability_sidecar_indexer" {
  for_each = local.observability_sidecar_enabled ? local.env.enabled_nets : {}
  source   = "../modules/observability-sidecar"

  service_name      = "indexer"
  env               = local.observability_env
  deployment        = local.observability_deployment
  network           = local.network_label[each.key]
  aws_region        = local.env.region
  amp_endpoint      = data.terraform_remote_state.observability.outputs.amp_endpoint
  amp_workspace_arn = data.terraform_remote_state.observability.outputs.amp_workspace_arn
  app_port          = 8080
}

module "observability_sidecar_api" {
  for_each = local.observability_sidecar_enabled ? local.env.enabled_nets : {}
  source   = "../modules/observability-sidecar"

  service_name      = "api"
  env               = local.observability_env
  deployment        = local.observability_deployment
  network           = local.network_label[each.key]
  aws_region        = local.env.region
  amp_endpoint      = data.terraform_remote_state.observability.outputs.amp_endpoint
  amp_workspace_arn = data.terraform_remote_state.observability.outputs.amp_workspace_arn
  app_port          = 8080
}

# ecs-lb-service-api has no extra_statements input, so attach the API
# sidecar's aps:RemoteWrite policy directly to the task role by name.
resource "aws_iam_role_policy" "api_sidecar_amp_remote_write" {
  for_each = local.observability_sidecar_enabled ? local.env.enabled_nets : {}

  name       = "amp-remote-write"
  role       = "${local.env.environment}-${var.project}-${each.key}-api-ecs-role"
  policy     = module.observability_sidecar_api[each.key].amp_remote_write_policy_json
  depends_on = [module.ecs-lb-service-api]
}
