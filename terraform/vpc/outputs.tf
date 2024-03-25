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

output "currency_cache_name" {
  description = "The name of the api gateway rest api currency cache"
  value       = local.env.environment == "prod" ? aws_api_gateway_rest_api.currency_cache[0].name : null
}

# output "cloudwatch_log_group_name" {
#     value = module.vpclogs_cloudwatch.log_group_name
# }

# output "s3_bucket_name" {
#     value = module.vpclogs_s3.bucket_name
# }
