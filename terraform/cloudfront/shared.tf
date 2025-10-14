# Shared CloudFront Resources (Cache Policies and WAF)
# This module is deployed in the 'shared' workspace

# Cache Policies v1 - Current stable version
module "cache_policies_v1" {
  count = local.is_shared ? length(try(local.env_config.cache_policies_v1, [])) : 0
  
  source = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  
  cache_policy         = try(local.env_config.cache_policies_v1[count.index].name, "")
  headers_policy       = null  # Not creating header policies - using managed policy IDs
  create_header_policy = 0     # Disabled - using managed header policy IDs
  default_ttl          = try(local.env_config.cache_policies_v1[count.index].default_ttl_seconds, 0)
  max_ttl              = try(local.env_config.cache_policies_v1[count.index].max_ttl_seconds, 0)
  min_ttl              = try(local.env_config.cache_policies_v1[count.index].min_ttl_seconds, 0)
  query_string_behavior = try(local.env_config.cache_policies_v1[count.index].query_string_behavior, "none")
  header_behavior = try(local.env_config.cache_policies_v1[count.index].header_behavior, "none")
  cookie_behavior = try(local.env_config.cache_policies_v1[count.index].cookie_behavior, "none")
  enable_gzip = try(local.env_config.cache_policies_v1[count.index].enable_gzip, false)
  enable_brotli = try(local.env_config.cache_policies_v1[count.index].enable_brotli, false)
  providers = {
    aws = aws.us_east_1
  }
}

# Cache Policies v2 - Next version for testing
module "cache_policies_v2" {
  count = local.is_shared ? length(try(local.env_config.cache_policies_v2, [])) : 0
  
  source = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  
  cache_policy         = try(local.env_config.cache_policies_v2[count.index].name, "")
  headers_policy       = null  # Not creating header policies - using managed policy IDs
  create_header_policy = 0     # Disabled - using managed header policy IDs
  default_ttl          = try(local.env_config.cache_policies_v2[count.index].default_ttl_seconds, 0)
  max_ttl              = try(local.env_config.cache_policies_v2[count.index].max_ttl_seconds, 0)
  min_ttl              = try(local.env_config.cache_policies_v2[count.index].min_ttl_seconds, 0)
  query_string_behavior = try(local.env_config.cache_policies_v2[count.index].query_string_behavior, "none")
  header_behavior = try(local.env_config.cache_policies_v2[count.index].header_behavior, "none")
  cookie_behavior = try(local.env_config.cache_policies_v2[count.index].cookie_behavior, "none")
  enable_gzip = try(local.env_config.cache_policies_v2[count.index].enable_gzip, false)
  enable_brotli = try(local.env_config.cache_policies_v2[count.index].enable_brotli, false)
  providers = {
    aws = aws.us_east_1
  }
}

# WAF Configuration
module "waf" {
  count = local.is_shared && try(local.env_config.waf.enable_waf, false) ? 1 : 0
  
  source = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//waf?ref=cloudfront-changes"
  
  env                       = local.env_config.environment
  project_name              = local.env_config.project_name
  waf_cloudfront_enable     = try(local.env_config.waf.waf_cloudfront_enable, false)
  logs_enable               = try(local.env_config.waf.waf_logs_enable, false)
  logs_s3_enable            = try(local.env_config.waf.waf_logs_s3_enable, false)
  logs_retension            = try(local.env_config.waf.waf_logs_retention, 30)
  scope                     = try(local.env_config.waf.waf_scope, "CLOUDFRONT")
  associate_waf             = try(local.env_config.waf.waf_associate, false)
  rate_limit                = try(local.env_config.waf.waf_rate_limit, 1000)
  rate_limit_exception_list = try(local.env_config.waf.waf_rate_limit_exceptions, [])
  
  managed_rule_group_statement_rules = [
    for rule in try(local.env_config.waf_managed_rules, []) : {
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
  
  providers = {
    aws = aws.us_east_1
  }
}

