# Shared CloudFront Resources (Cache Policies and WAF)
# This module is deployed in the 'shared' workspace

# Cache Policies v1 - Current stable version
module "cache_policies_v1" {
  count = local.is_shared ? length(try(local.env_config.cache_policies_v1, [])) : 0

  source = "./modules/policies"

  cache_policy          = try(local.env_config.cache_policies_v1[count.index].name, "")
  headers_policy        = null # Not creating header policies - using managed policy IDs
  create_header_policy  = 0    # Disabled - using managed header policy IDs
  default_ttl           = try(local.env_config.cache_policies_v1[count.index].default_ttl_seconds, 0)
  max_ttl               = try(local.env_config.cache_policies_v1[count.index].max_ttl_seconds, 0)
  min_ttl               = try(local.env_config.cache_policies_v1[count.index].min_ttl_seconds, 0)
  query_string_behavior = try(local.env_config.cache_policies_v1[count.index].query_string_behavior, "none")
  header_behavior       = try(local.env_config.cache_policies_v1[count.index].header_behavior, "none")
  header_items          = try(local.env_config.cache_policies_v1[count.index].header_items, [])
  cookie_behavior       = try(local.env_config.cache_policies_v1[count.index].cookie_behavior, "none")
  enable_gzip           = try(local.env_config.cache_policies_v1[count.index].enable_gzip, false)
  enable_brotli         = try(local.env_config.cache_policies_v1[count.index].enable_brotli, false)
  providers = {
    aws = aws.us_east_1
  }
}

# Cache Policies v2 - Next version for testing
module "cache_policies_v2" {
  count = local.is_shared ? length(try(local.env_config.cache_policies_v2, [])) : 0

  source = "./modules/policies"

  cache_policy          = try(local.env_config.cache_policies_v2[count.index].name, "")
  headers_policy        = null # Not creating header policies - using managed policy IDs
  create_header_policy  = 0    # Disabled - using managed header policy IDs
  default_ttl           = try(local.env_config.cache_policies_v2[count.index].default_ttl_seconds, 0)
  max_ttl               = try(local.env_config.cache_policies_v2[count.index].max_ttl_seconds, 0)
  min_ttl               = try(local.env_config.cache_policies_v2[count.index].min_ttl_seconds, 0)
  query_string_behavior = try(local.env_config.cache_policies_v2[count.index].query_string_behavior, "none")
  header_behavior       = try(local.env_config.cache_policies_v2[count.index].header_behavior, "none")
  header_items          = try(local.env_config.cache_policies_v2[count.index].header_items, [])
  cookie_behavior       = try(local.env_config.cache_policies_v2[count.index].cookie_behavior, "none")
  enable_gzip           = try(local.env_config.cache_policies_v2[count.index].enable_gzip, false)
  enable_brotli         = try(local.env_config.cache_policies_v2[count.index].enable_brotli, false)
  providers = {
    aws = aws.us_east_1
  }
}

# The two CLOUDFRONT-scope ACLs these managed are owned by terraform/vpc
# (PR #1519). Forget them here rather than destroy them: they are live.
removed {
  from = module.waf

  lifecycle {
    destroy = false
  }
}

removed {
  from = module.testnet_waf

  lifecycle {
    destroy = false
  }
}
