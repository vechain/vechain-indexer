# Outputs for Remote State Consumption
# These outputs will be consumed by prod and staging environments via remote state

output "cache_policy_map" {
  description = "Mapping from cache policy names to cache policy IDs (V1 - used by staging and prod)"
  value = {
    for idx, policy in local.env.cache_policies_v1 : policy.id => {
      cache_policy_id = module.cache_policies_v1[idx].cache_policy_id
    }
  }
}

output "waf_arn" {
  description = "WAF ARN for use by CloudFront distributions"
  value       = local.env.waf.enable_waf && length(module.waf) > 0 ? module.waf[0].waf_arn : null
}

output "waf_count" {
  description = "Number of WAF instances created (0 or 1)"
  value       = length(module.waf)
}

# Additional outputs that environments might need
output "cache_policies" {
  description = "Complete cache policies V1 configuration (used by staging and prod)"
  value       = local.env.cache_policies_v1
}

output "cache_policies_v2" {
  description = "Complete cache policies V2 configuration"
  value       = local.env.cache_policies_v2
}

# Additional cache policy maps for different versions
output "cache_policy_map_v2" {
  description = "Mapping from cache policy names to cache policy IDs (V2)"
  value = {
    for idx, policy in local.env.cache_policies_v2 : policy.id => {
      cache_policy_id = module.cache_policies_v2[idx].cache_policy_id
    }
  }
}

output "waf_config" {
  description = "WAF configuration"
  value       = local.env.waf
}

output "environment" {
  description = "Environment name"
  value       = local.env.environment
}

output "project_name" {
  description = "Project name"
  value       = local.env.project_name
}
