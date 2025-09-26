# Modules

module "staging_mainnet_cloudfront" {
  source          = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
  origin_domain   = local.env.mainnet_origin_domain
  staging  = local.env.staging
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
}

module "staging_testnet_cloudfront" {
  source          = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
  origin_domain   = local.env.testnet_origin_domain
  staging  = local.env.staging
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
