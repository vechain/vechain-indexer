# Independent Shared Cache Policies and WAF Module
# This module creates cache policies and WAF using shared.yml configuration

# Cache Policies Module V1
module "cache_policies_v1" {
  count = length(local.env.cache_policies_v1)
  
  source               = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy         = local.env.cache_policies_v1[count.index].name
  headers_policy       = null  # Not creating header policies - using managed policy IDs
  create_header_policy = 0     # Disabled - using managed header policy IDs
  default_ttl          = local.env.cache_policies_v1[count.index].default_ttl_seconds
  max_ttl              = local.env.cache_policies_v1[count.index].max_ttl_seconds
  min_ttl              = local.env.cache_policies_v1[count.index].min_ttl_seconds
}

# Cache Policies Module V2
module "cache_policies_v2" {
  count = length(local.env.cache_policies_v2)
  
  source               = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy         = local.env.cache_policies_v2[count.index].name
  headers_policy       = null  # Not creating header policies - using managed policy IDs
  create_header_policy = 0     # Disabled - using managed header policy IDs
  default_ttl          = local.env.cache_policies_v2[count.index].default_ttl_seconds
  max_ttl              = local.env.cache_policies_v2[count.index].max_ttl_seconds
  min_ttl              = local.env.cache_policies_v2[count.index].min_ttl_seconds
}
# WAF Module
module "waf" {
  # Note: Warning about undefined provider is harmless - external module doesn't declare required_providers
  providers = {
    aws = aws.us_east_1
  }
  count                     = local.env.waf.enable_waf ? 1 : 0
  source                    = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//waf?ref=cloudfront-changes"
  env                       = local.env.environment
  project_name              = local.env.project_name
  waf_cloudfront_enable     = local.env.waf.waf_cloudfront_enable
  logs_enable               = local.env.waf.waf_logs_enable
  logs_s3_enable            = local.env.waf.waf_logs_s3_enable
  logs_retension            = local.env.waf.waf_logs_retention
  scope                     = local.env.waf.waf_scope
  associate_waf             = local.env.waf.waf_associate
  rate_limit                = local.env.waf.waf_rate_limit
  rate_limit_exception_list = local.env.waf.waf_rate_limit_exceptions
  
  managed_rule_group_statement_rules = [
    for rule in local.env.waf_managed_rules : {
      name            = rule.name
      priority        = rule.priority
      override_action = rule.override_action
      managed_rule_group_statement = [{
        name          = rule.rule_group_name
        vendor_name   = rule.vendor_name
        excluded_rule = rule.excluded_rules
      }]
      
    }
  ]
}
