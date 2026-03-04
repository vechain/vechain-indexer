output "vpc_id" {
  description = "The ID of the VPC"
  value       = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
}

output "vpc_ipv4" {
  description = "The IPv4 CIDR block of the VPC"
  value       = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.cidr_block : module.vpc[0].vpc_cidr_block
}

output "private_subnets" {
  description = "The IDs of the private subnets"
  value       = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : data.aws_subnets.dev_priv_subnets.ids
}

output "public_subnets" {
  description = "The IDs of the public subnets"
  value       = local.env.environment == "prod" ? data.aws_subnets.ct_pub_subnets.ids : data.aws_subnets.dev_pub_subnets.ids
}

output "database_subnets" {
  description = "The IDs of the database subnets"
  value       = local.env.environment == "prod" ? null : data.aws_subnets.database_subnets.ids
}

output "chatbot_sns_topic_name" {
  description = "The name of the chatbot/slack SNS topic"
  value       = aws_sns_topic.chatbot_sns_topic.name
}

output "atlas_export_bucket_id" {
  description = "The S3 bucket ID for use in backup schedule export blocks"
  value       = local.env.environment == "prod" ? aws_s3_bucket.atlas_export_backups[0].id : ""
}
