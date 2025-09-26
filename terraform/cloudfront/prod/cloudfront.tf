
# Continuous deployment configuration is now controlled via prod.yml
# Access via local.env.enable_continuous_deployment

# Data Sources - Read staging CloudFront outputs for continuous deployment
data "terraform_remote_state" "staging" {
  count   = local.env.enable_continuous_deployment ? 1 : 0
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-prod"  # Replace with your actual S3 bucket
    key    = "staging/veworld-indexer-cloudfront.tfstate"  # Staging state key
    region = "eu-west-1"
  }
}

# Modules

module "mainnet_cloudfront" {
  source          = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
  origin_domain   = local.env.mainnet_origin_domain
  certificate_arn = local.env.mainnet_certificate_arn
  cnames          = local.env.mainnet_cnames
  ordered_cache_behaviors = [
    for behavior in local.env.cache_behaviors : {
      path_pattern           = behavior.path_pattern
      target_origin_id       = "origin-${local.env.mainnet_origin_domain}"
      cache_policy_id        = local.cache_policy_map[behavior.cache_policy_name].cache_policy_id
      headers_policy_id      = behavior.header_policy_id
      allowed_methods        = behavior.allowed_methods
      cached_methods         = behavior.cached_methods
      viewer_protocol_policy = behavior.viewer_protocol_policy
      origin_request_policy_id = behavior.origin_request_policy_id
    }
  ]
  waf_web_acl = data.terraform_remote_state.shared.outputs.waf_arn
  cache_policy_id        = local.cache_policy_map["default"].cache_policy_id
  headers_policy_id      = local.env.default_headers_policy_id
  origin_request_policy_id = local.env.default_origin_request_policy_id
  # Continuous deployment configuration
 continuous_deployment_policy_id = local.env.enable_continuous_deployment ? aws_cloudfront_continuous_deployment_policy.mainnet_continuous_deployment[0].id : null
}

module "testnet_cloudfront" {
  source          = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
  origin_domain   = local.env.testnet_origin_domain
  certificate_arn = local.env.testnet_certificate_arn
  cnames          = local.env.testnet_cnames
  ordered_cache_behaviors = [
    for behavior in local.env.cache_behaviors : {
      path_pattern           = behavior.path_pattern
      target_origin_id       = "origin-${local.env.testnet_origin_domain}"
      cache_policy_id        = local.cache_policy_map[behavior.cache_policy_name].cache_policy_id
      headers_policy_id      = behavior.header_policy_id
      allowed_methods        = behavior.allowed_methods
      cached_methods         = behavior.cached_methods
      viewer_protocol_policy = behavior.viewer_protocol_policy
      origin_request_policy_id = behavior.origin_request_policy_id
    }
  ]
  waf_web_acl = data.terraform_remote_state.shared.outputs.waf_arn
  cache_policy_id        = local.cache_policy_map["default"].cache_policy_id
  headers_policy_id      = local.env.default_headers_policy_id
  origin_request_policy_id = local.env.default_origin_request_policy_id
  
  # Continuous deployment configuration
  continuous_deployment_policy_id = local.env.enable_continuous_deployment ? aws_cloudfront_continuous_deployment_policy.testnet_continuous_deployment[0].id : null
}

# Remote State Data Source for Shared Resources
data "terraform_remote_state" "shared" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-prod"
    key    = "shared/veworld-indexer-cloudfront.tfstate"
    region = "eu-west-1"
  }
}


# Continuous Deployment Policy - Uses staging CloudFront domains
resource "aws_cloudfront_continuous_deployment_policy" "mainnet_continuous_deployment" {
  count   = local.env.enable_continuous_deployment ? 1 : 0
  enabled = true

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
  count   = local.env.enable_continuous_deployment ? 1 : 0
  enabled = true

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