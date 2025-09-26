# Production CloudFront Distribution Outputs
# These outputs are used by the GitHub Actions workflow for continuous deployment

output "primary_mainnet_cloudfront_distribution_id" {
  description = "Primary production CloudFront distribution ID (mainnet)"
  value       = module.mainnet_cloudfront.distribution
  sensitive   = false
}

output "staging_distribution_id" {
  description = "Staging CloudFront distribution ID (mainnet) - retrieved from staging remote state"
  value       = local.env.enable_continuous_deployment ? try(data.terraform_remote_state.staging[0].outputs.staging_mainnet_cloudfront_distribution_id, null) : null
  sensitive   = false
}


output "primary_testnet_cloudfront_distribution_id" {
  description = "Production testnet CloudFront distribution ID"
  value       = module.testnet_cloudfront.distribution
  sensitive   = false
}

output "staging_testnet_cloudfront_distribution_id" {
  description = "Staging testnet CloudFront distribution ID"
  value       = local.env.enable_continuous_deployment ? try(data.terraform_remote_state.staging[0].outputs.staging_testnet_cloudfront_distribution_id, null) : null
  sensitive   = false
}

output "continuous_deployment_enabled" {
  description = "Whether continuous deployment is enabled"
  value       = local.env.enable_continuous_deployment
  sensitive   = false
}


# Remote state validation - helps with debugging
output "staging_remote_state_available" {
  description = "Whether staging remote state is accessible"
  value       = local.env.enable_continuous_deployment ? length(data.terraform_remote_state.staging) > 0 : false
  sensitive   = false
}
