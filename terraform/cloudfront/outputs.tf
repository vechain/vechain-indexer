# Outputs for CloudFront Infrastructure
# These outputs are workspace-aware and only create outputs when appropriate

# Data sources to get CloudFront distribution details
data "aws_cloudfront_distribution" "mainnet_distribution" {
  count = local.is_shared ? 0 : (length(module.mainnet_cloudfront) > 0 ? 1 : 0)
  id    = length(module.mainnet_cloudfront) > 0 ? module.mainnet_cloudfront[0].distribution : null
}

data "aws_cloudfront_distribution" "testnet_distribution" {
  count = local.is_shared ? 0 : (length(module.testnet_cloudfront) > 0 ? 1 : 0)
  id    = length(module.testnet_cloudfront) > 0 ? module.testnet_cloudfront[0].distribution : null
}

# Cache Policy Outputs (Shared workspace only)
output "cache_policy_map" {
  description = "Map of cache policy IDs by name"
  value = local.is_shared ? {
    for i, policy in module.cache_policies_v1 : local.env_config.cache_policies_v1[i].id => {
      cache_policy_id = policy.cache_policy_id
    }
  } : null
}

# WAF Outputs (Shared workspace only)
output "waf_arn" {
  description = "ARN of the WAF Web ACL"
  value = local.is_shared && length(module.waf) > 0 ? module.waf[0].waf_arn : null
}

# CloudFront Distribution Outputs (Staging and Prod workspaces only)
output "mainnet_cloudfront_domain_name" {
  description = "Domain name of the mainnet CloudFront distribution"
  value = local.is_shared ? null : (length(data.aws_cloudfront_distribution.mainnet_distribution) > 0 ? data.aws_cloudfront_distribution.mainnet_distribution[0].domain_name : null)
}

output "mainnet_cloudfront_distribution_id" {
  description = "ID of the mainnet CloudFront distribution"
  value = local.is_shared ? null : (length(module.mainnet_cloudfront) > 0 ? module.mainnet_cloudfront[0].distribution : null)
}

output "testnet_cloudfront_domain_name" {
  description = "Domain name of the testnet CloudFront distribution"
  value = local.is_shared ? null : (length(data.aws_cloudfront_distribution.testnet_distribution) > 0 ? data.aws_cloudfront_distribution.testnet_distribution[0].domain_name : null)
}

output "testnet_cloudfront_distribution_id" {
  description = "ID of the testnet CloudFront distribution"
  value = local.is_shared ? null : (length(module.testnet_cloudfront) > 0 ? module.testnet_cloudfront[0].distribution : null)
}

# Staging-specific outputs (for continuous deployment)
output "staging_mainnet_cloudfront_domain_name" {
  description = "Domain name of the staging mainnet CloudFront distribution"
  value = local.is_staging && length(data.aws_cloudfront_distribution.mainnet_distribution) > 0 ? data.aws_cloudfront_distribution.mainnet_distribution[0].domain_name : null
}

output "staging_testnet_cloudfront_domain_name" {
  description = "Domain name of the staging testnet CloudFront distribution"
  value = local.is_staging && length(data.aws_cloudfront_distribution.testnet_distribution) > 0 ? data.aws_cloudfront_distribution.testnet_distribution[0].domain_name : null
}
