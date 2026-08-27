output "cache_policy_id" {
  value       = aws_cloudfront_cache_policy.cache_policy.id
  description = "AWS Cloudfront Cache Policy ID"
}

output "headers_policy_id" {
  value       = length(aws_cloudfront_response_headers_policy.header_policy) > 0 ? aws_cloudfront_response_headers_policy.header_policy[0].id : null
  description = "AWS Cloudfront Response Headers Policy ID"
}
