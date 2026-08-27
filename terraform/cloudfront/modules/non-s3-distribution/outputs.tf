output "distribution" {
  value = aws_cloudfront_distribution.non_s3_distribution.id
}

output "distribution_arn" {
  value = aws_cloudfront_distribution.non_s3_distribution.arn
}

output "domain_name" {
  value = aws_cloudfront_distribution.non_s3_distribution.domain_name
}
