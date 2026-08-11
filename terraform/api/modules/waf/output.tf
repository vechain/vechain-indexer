output "waf_limiter_arn" {
  description = "WAF Web ACL rate limiter ARN (regional)"
  value       = try(aws_wafv2_web_acl.rate_limiter[0].arn, "")
}

output "waf_cloudfront_arn" {
  description = "WAF Web ACL ARN for CloudFront (global)"
  value       = try(aws_wafv2_web_acl.waf_cloudfront[0].arn, "")
}

output "waf_regional_association_ids" {
  description = "Map of resource ARNs to their WAF association IDs"
  value       = { for k, v in aws_wafv2_web_acl_association.regional_association : k => v.id }
}
