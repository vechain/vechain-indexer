output "vpc_id" {
  description = "The ID of the VPC"
  value       = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
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

# output "cloudwatch_log_group_name" {
#     value = module.vpclogs_cloudwatch.log_group_name
# }

# output "s3_bucket_name" {
#     value = module.vpclogs_s3.bucket_name
# }
