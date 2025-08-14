
module "indexer_cachewaf" {
  source                             = "git@github.com:vechain/terraform_infrastructure_modules.git//waf"
  for_each                           = { for key, network in try(local.env.waf_profile.networks, {}) : key => key }
  env                                = local.env.environment
  project_name                       = "indexer-waf-${each.value}"
  waf_cloudfront_enable              = true
  waf_regional_enable                = true
  scope                              = "CLOUDFRONT"
  global_rule                        = "testnet-glob-rl"
  logs_s3_enable                     = true
  logs_retension                     = 90
  logging_redacted_fields            = local.env.waf_profile.logging_redacted_fields
  logging_filter                     = local.env.waf_profile.logging_filter
  rate_based_statement_rules         = local.env.waf_profile.rate_based_rules
  rate_limit                         = 1000
  managed_rule_group_statement_rules = local.env.waf_profile.managed_rule_group_statement_rules
}

module "indexer_cloudfront" {
  source              = "git@github.com:vechain/terraform_infrastructure_modules.git//cloudfront/assets"
  for_each            = { for key, network in try(local.env.waf_profile.networks, {}) : key => key }
  description         = "indexercache-${each.value}"
  non_s3_domain       = local.env.waf_profile.networks[each.value].origin_domain
  cnames              = local.env.waf_profile.networks[each.value].cnames
  certificate_arn     = local.env.certificate_arn
  api_paths           = local.env.cdn_api_paths
  cache_policy_name   = "indexercache-${each.value}-cpol"
  headers_policy_name = "indexercache-${each.value}-hpol"
  waf_web_acl         = module.indexer_cachewaf[each.value].waf_limiter_arn
  min_ttl             = 1
  default_ttl         = 300
  max_ttl             = 3600
}

