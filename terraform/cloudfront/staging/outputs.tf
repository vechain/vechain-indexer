# CloudFront Distribution Outputs for Continuous Deployment




 output "staging_mainnet_cloudfront_domain_name" {
  description = "Staging mainnet CloudFront distribution domain name (e.g., xyz.cloudfront.net)"
  value       = module.staging_mainnet_cloudfront.domain_name
 }

# Domain names - Commented out until correct attribute names are confirmed
output "staging_testnet_cloudfront_domain_name" {
  description = "Staging mainnet CloudFront distribution domain name (e.g., xyz.cloudfront.net)"
  value       = module.staging_testnet_cloudfront.domain_name
}

output "staging_testnet_cloudfront_distribution_id" {
  description = "Staging testnet CloudFront distribution ID"
  value       = module.staging_testnet_cloudfront.distribution
}

output "staging_mainnet_cloudfront_distribution_id" {
  description = "Staging testnet CloudFront distribution ID"
  value       = module.staging_mainnet_cloudfront.distribution
}