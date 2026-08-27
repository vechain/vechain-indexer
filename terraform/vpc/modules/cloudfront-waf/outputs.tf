output "web_acl_arn" {
  description = "ARN of the Web ACL. Reference this from the CloudFront distribution's web_acl_id once the distributions are in Terraform."
  value       = aws_wafv2_web_acl.this.arn
}

output "web_acl_id" {
  value = aws_wafv2_web_acl.this.id
}

output "web_acl_name" {
  value = aws_wafv2_web_acl.this.name
}
