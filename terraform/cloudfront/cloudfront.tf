# CloudFront Distributions Configuration
# This handles both staging and production distributions based on workspace

# Local variables for CloudFront configuration
locals {
  # Cache policy mapping - references shared resources
  cache_policy_map = local.is_shared ? {} : try(data.terraform_remote_state.shared[0].outputs.cache_policy_map, {})

  # WAF ARNs come from terraform/vpc, which owns these ACLs.
  waf_arn         = local.is_shared ? null : try(data.terraform_remote_state.vpc[0].outputs.cloudfront_waf_mainnet_arn, null)
  testnet_waf_arn = local.is_shared ? null : try(data.terraform_remote_state.vpc[0].outputs.cloudfront_waf_testnet_arn, null)

  origin_verify_secret_name = "/prod/veworld/cloudfront-origin-verify-token"
  origin_verify_header_name = try(local.env_config.origin_verify_header_name, "")

  # Staging twins carry the same header, so enabling a continuous-deployment
  # policy later cannot start failing at the origin.
  origin_verify_headers = local.is_shared || local.origin_verify_header_name == "" ? {} : {
    (local.origin_verify_header_name) = try(data.aws_secretsmanager_secret_version.origin_verify[0].secret_string, "")
  }
}

# Same describe-or-create pattern as the WAF bypass token in terraform/api, so a
# first apply does not need the secret to exist already.
resource "null_resource" "ensure_origin_verify_secret" {
  count = local.is_shared ? 0 : 1

  triggers = {
    secret_name = local.origin_verify_secret_name
  }

  provisioner "local-exec" {
    command = <<-EOT
      aws secretsmanager describe-secret --secret-id "${local.origin_verify_secret_name}" --region ${local.env_config.region} 2>/dev/null || \
      aws secretsmanager create-secret \
        --name "${local.origin_verify_secret_name}" \
        --secret-string "$(openssl rand -hex 32)" \
        --region ${local.env_config.region}
    EOT
  }
}

data "aws_secretsmanager_secret_version" "origin_verify" {
  count      = local.is_shared ? 0 : 1
  secret_id  = local.origin_verify_secret_name
  depends_on = [null_resource.ensure_origin_verify_secret]
}

# Mainnet CloudFront Distribution
module "mainnet_cloudfront" {
  count = local.is_shared ? 0 : 1

  source = "./modules/non-s3-distribution"

  origin_domain         = try(local.env_config.mainnet_origin_domain, "")
  certificate_arn       = try(local.env_config.mainnet_certificate_arn, "")
  cnames                = try(local.env_config.mainnet_cnames, [])
  staging               = try(local.env_config.staging, false)
  origin_custom_headers = local.origin_verify_headers

  ordered_cache_behaviors = [
    for behavior in try(local.env_config.cache_behaviors, []) : {
      path_pattern             = behavior.path_pattern
      target_origin_id         = "origin-${try(local.env_config.mainnet_origin_domain, "")}"
      cache_policy_id          = try(local.cache_policy_map[behavior.cache_policy_name].cache_policy_id, "")
      headers_policy_id        = behavior.header_policy_id
      allowed_methods          = behavior.allowed_methods
      cached_methods           = behavior.cached_methods
      viewer_protocol_policy   = behavior.viewer_protocol_policy
      origin_request_policy_id = behavior.origin_request_policy_id
    }
  ]

  waf_web_acl              = local.waf_arn
  cache_policy_id          = try(local.cache_policy_map["default"].cache_policy_id, "")
  headers_policy_id        = try(local.env_config.default_headers_policy_id, "")
  origin_request_policy_id = try(local.env_config.default_origin_request_policy_id, "")

  # Continuous deployment configuration (prod only)
  continuous_deployment_policy_id = local.is_prod && try(local.env_config.enable_continuous_deployment, false) ? aws_cloudfront_continuous_deployment_policy.mainnet_continuous_deployment[0].id : null
}

# Testnet CloudFront Distribution
module "testnet_cloudfront" {
  count = local.is_shared ? 0 : 1

  source = "./modules/non-s3-distribution"

  origin_domain         = try(local.env_config.testnet_origin_domain, "")
  certificate_arn       = try(local.env_config.testnet_certificate_arn, "")
  cnames                = try(local.env_config.testnet_cnames, [])
  staging               = try(local.env_config.staging, false)
  origin_custom_headers = local.origin_verify_headers

  ordered_cache_behaviors = [
    for behavior in try(local.env_config.cache_behaviors, []) : {
      path_pattern             = behavior.path_pattern
      target_origin_id         = "origin-${try(local.env_config.testnet_origin_domain, "")}"
      cache_policy_id          = try(local.cache_policy_map[behavior.cache_policy_name].cache_policy_id, "")
      headers_policy_id        = behavior.header_policy_id
      allowed_methods          = behavior.allowed_methods
      cached_methods           = behavior.cached_methods
      viewer_protocol_policy   = behavior.viewer_protocol_policy
      origin_request_policy_id = behavior.origin_request_policy_id
    }
  ]

  waf_web_acl              = local.testnet_waf_arn
  cache_policy_id          = try(local.cache_policy_map["default"].cache_policy_id, "")
  headers_policy_id        = try(local.env_config.default_headers_policy_id, "")
  origin_request_policy_id = try(local.env_config.default_origin_request_policy_id, "")

  # Continuous deployment configuration (prod only)
  continuous_deployment_policy_id = local.is_prod && try(local.env_config.enable_continuous_deployment, false) ? aws_cloudfront_continuous_deployment_policy.testnet_continuous_deployment[0].id : null
}

# Continuous Deployment Policies (Production only)
resource "aws_cloudfront_continuous_deployment_policy" "mainnet_continuous_deployment" {
  count = local.is_prod && try(local.env_config.enable_continuous_deployment, false) ? 1 : 0

  # Whether the policy routes to staging, as opposed to merely existing.
  enabled = try(local.env_config.continuous_deployment_enabled, false)

  staging_distribution_dns_names {
    items = [
      try(data.terraform_remote_state.staging[0].outputs.staging_mainnet_cloudfront_domain_name, null)
    ]
    quantity = 1
  }

  traffic_config {
    type = "SingleHeader"
    single_header_config {
      header = "aws-cf-cd-staging"
      value  = "mainnet"
    }
  }
}

resource "aws_cloudfront_continuous_deployment_policy" "testnet_continuous_deployment" {
  count = local.is_prod && try(local.env_config.enable_continuous_deployment, false) ? 1 : 0

  # Whether the policy routes to staging, as opposed to merely existing.
  enabled = try(local.env_config.continuous_deployment_enabled, false)

  staging_distribution_dns_names {
    items = [
      try(data.terraform_remote_state.staging[0].outputs.staging_testnet_cloudfront_domain_name, null)
    ]
    quantity = 1
  }

  traffic_config {
    type = "SingleHeader"
    single_header_config {
      header = "aws-cf-cd-staging"
      value  = "testnet"
    }
  }
}

