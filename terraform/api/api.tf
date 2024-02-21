variable "project" {
  default = "veworld"
}

data "aws_vpc" "ct_vpc_id" {
  filter {
    name   = "tag:Name"
    values = ["aws-controltower-VPC", "${local.env.environment}-${var.project}-vpc"]
  }
}

data "aws_subnets" "ct_pub_subnets" {
  filter {
    name   = "tag:Name"
    values = ["aws-controltower-PublicSubnet*"]
  }
}

data "aws_subnets" "ct_priv_subnets" {
  filter {
    name   = "tag:Name"
    values = ["aws-controltower-PrivateSubnet*"]
  }
}

module "vpc" {
  count   = local.env.environment == "prod" ? 0 : 1
  source  = "terraform-aws-modules/vpc/aws"
  version = "v4.0.1"
  cidr    = local.env.cidr
  name    = "${local.env.environment}-${var.project}-vpc"
  azs     = local.env.azs
  # Compact subnetting up to 4 AZs, by up to 4 subnets by x/24 cidr blocks neatly fits in /20 172.31.0.0/16 fits 16 of them
  # AZ's are offset by 4 with subnets if each AZ sequential neighbours
  public_subnets         = local.env.public_subnets
  private_subnets        = local.env.private_subnets
  database_subnets       = local.env.database_subnets
  enable_nat_gateway     = true
  single_nat_gateway     = false
  one_nat_gateway_per_az = true

  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Environment = local.env.environment
    Terraform   = "true"
    Project     = var.project
  }
}

# Modules
module "vpclogs_cloudwatch" {
  source                                          = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//logs"
  environment                                     = local.env.environment
  create_flow_log_cloudwatch_log_group            = "true"
  create_flow_log_cloudwatch_iam_role             = "true"
  flow_log_log_format                             = null
  flow_log_traffic_type                           = "ALL"
  vpc_id                                          = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
  flow_log_max_aggregation_interval               = 600
  flow_log_destination_type                       = "cloud-watch-logs"
  flow_log_file_format                            = "plain-text"
  flow_log_hive_compatible_partitions             = "false"
  flow_log_per_hour_partition                     = "false"
  flow_log_cloudwatch_log_group_retention_in_days = 30
  flow_log_cloudwatch_log_group_kms_key_id        = null
  vpc_flow_log_permissions_boundary               = null
  enable_flow_log_cloudwatch                      = "true"
}


module "vpclogs_s3" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//logs"
  environment                         = local.env.environment
  app_name                            = var.project
  flow_log_log_format                 = null
  flow_log_traffic_type               = "ALL"
  vpc_id                              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
  flow_log_max_aggregation_interval   = 600
  flow_log_destination_type           = "s3"
  flow_log_file_format                = "plain-text"
  flow_log_hive_compatible_partitions = "false"
  flow_log_per_hour_partition         = "false"
  vpc_flow_log_permissions_boundary   = null
  enable_flow_log_s3                  = "false"
  create_s3_bucket                    = "false"
}

# VPC
output "vpc_id" {
  description = "The ID of the VPC"
  value       = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
}

resource "aws_service_discovery_private_dns_namespace" "ns" {
  name = "${local.env.environment}.${var.project}"
  vpc  = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
}

################################################################################
# Module For ECS Load Balanced Service API
################################################################################

module "ecs-lb-service" {
  for_each                   = local.env.enabled_nets
  source                     = "git::git@github.com:/vechainfoundation/devops.git//ecs"
  vpc_id                     = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
  public_subnets             = local.env.environment == "prod" ? data.aws_subnets.ct_pub_subnets.ids : module.vpc[0].public_subnets
  private_subnets            = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : module.vpc[0].private_subnets
  env                        = local.env.environment
  common_ecr_repo            = true
  common_ecr_repo_url        = each.value.api.ecr_common_repo
  internal_url_name          = "${each.key}.local"
  app_name                   = "${each.key}-api"
  image_tag                  = local.env.image_tag
  project                    = var.project
  cpu                        = each.value.api.cpu
  memory                     = each.value.api.memory
  cidr                       = local.env.cidr
  desired_capacity           = each.value.api.min_capacity
  container_port             = 8080
  host_port                  = 8080
  certificate_arn            = local.env.certificate_arn
  alb_path_rule              = ["/api/v*", "/api-docs", "/swagger-ui/*"]
  secrets_enable             = "false"
  lb_enable                  = "true"
  namespace                  = aws_service_discovery_private_dns_namespace.ns.id
  enable_deletion_protection = local.env.environment == "prod" ? true : false
  runtime_platform = [
    {
      operating_system_family = "LINUX"
      cpu_architecture        = "ARM64"
    }
  ]
  log_metric_filters = [
    {
      name    = "AppUnhealthy",
      pattern = "Application is UNHEALTHY"
    }
  ]

  environment_variables = [
    {
      name  = "APPLICATION_NAME"
      value = "api"
    },
    {
      name  = "ENVIRONMENT_NAME"
      value = local.env.environment
    },
    {
      name  = "SPRING_PROFILES_ACTIVE"
      value = each.value.api.spring_profile
    },
    {
      name  = "APP_LOG_LEVEL"
      value = "INFO"
    },
    { name  = "THOR_URL"
      value = each.value.thor_url
    },
    {
      name  = "MONGO_URI"
      value = format("%s://api:%s@%s/vechain?%s", each.value.mongodb.proto, urlencode(aws_ssm_parameter.mongo_api_password[0].value), each.value.mongodb.fqdn, each.value.mongodb.opts)
    },
    {
      name  = "MONGO_AUTHENTICATION_DATABASE",
      value = "admin"
    },
    {
      name  = "APP_LOGGER"
      value = "CloudWatch"
    }
  ]
}

################################################################################
# Common ECR repo across nets uses pre created repos as specified by ${local.env.<service>.ecr_common_repo}
################################################################################

################################################################################
# Module For ECS Non-Load Balanced Service API
################################################################################

module "ecs-service" {
  for_each            = local.env.enabled_nets
  source              = "git::git@github.com:/vechainfoundation/devops.git//ecs"
  vpc_id              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
  public_subnets      = local.env.environment == "prod" ? data.aws_subnets.ct_pub_subnets.ids : module.vpc[0].public_subnets
  private_subnets     = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : module.vpc[0].private_subnets
  env                 = local.env.environment
  common_ecr_repo     = true
  common_ecr_repo_url = each.value.indexer.ecr_common_repo
  image_tag           = local.env.image_tag
  internal_url_name   = "${each.key}.local"
  app_name            = "${each.key}-indexer"
  project             = var.project
  cpu                 = each.value.indexer.cpu
  memory              = each.value.indexer.memory
  cidr                = local.env.cidr
  desired_capacity    = "1"
  stop_then_start     = true
  container_port      = 8080
  host_port           = 8080
  secrets_enable      = "false"
  namespace           = aws_service_discovery_private_dns_namespace.ns.id
  runtime_platform = [
    {
      operating_system_family = "LINUX"
      cpu_architecture        = "ARM64"
    }
  ]
  log_metric_filters = [
    {
      name    = "AppUnhealthy",
      pattern = "Application is UNHEALTHY"
    }
  ]

  environment_variables = [
    {
      name  = "APPLICATION_NAME"
      value = "indexer"
    },
    {
      name  = "ENVIRONMENT_NAME"
      value = local.env.environment
    },
    {
      name  = "SPRING_PROFILES_ACTIVE"
      value = each.value.indexer.spring_profile
    },
    { name  = "THOR_URL"
      value = each.value.thor_url
    },
    {
      name  = "APP_LOG_LEVEL"
      value = "INFO"
    },
    {
      name  = "MONGO_URI"
      value = format("%s://indexer:%s@%s/vechain?%s", each.value.mongodb.proto, urlencode(aws_ssm_parameter.mongo_index_password[0].value), each.value.mongodb.fqdn, each.value.mongodb.opts)
    },
    {
      name  = "MONGO_AUTHENTICATION_DATABASE",
      value = "admin"
    },
    {
      name  = "APP_LOGGER"
      value = "CloudWatch"
    },
    {
      name  = "INDEXER_START_BLOCK_BLOCKS"
      value = each.value.indexer.start_block.blocks
    },
    {
      name  = "INDEXER_START_BLOCK_CLAUSES"
      value = each.value.indexer.start_block.clauses
    },
    {
      name  = "INDEXER_START_BLOCK_CONTRACTS"
      value = each.value.indexer.start_block.contracts
    },
    {
      name  = "INDEXER_START_BLOCK_NFTS"
      value = each.value.indexer.start_block.nfts
    },
    {
      name  = "INDEXER_START_BLOCK_TRANSACTIONS"
      value = each.value.indexer.start_block.transactions
    },
    {
      name  = "INDEXER_START_BLOCK_TRANSFERS"
      value = each.value.indexer.start_block.transfers
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_BLOCKS"
      value = each.value.indexer.sync_logger_interval.blocks
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_CLAUSES"
      value = each.value.indexer.sync_logger_interval.clauses
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_CONTRACTS"
      value = each.value.indexer.sync_logger_interval.contracts
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_FUNGIBLE_TOKENS"
      value = each.value.indexer.sync_logger_interval.fungible_tokens
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_NFTS"
      value = each.value.indexer.sync_logger_interval.nfts
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_TRANSACTIONS"
      value = each.value.indexer.sync_logger_interval.transactions
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_TRANSFERS"
      value = each.value.indexer.sync_logger_interval.transfers
    },
  ]
}

output "security_group_alb_id" {
  description = "The ID of ALB the security group"
  value       = [for security_group_alb_id in module.ecs-lb-service : security_group_alb_id]
}

output "security_group_ecs_service_id" {
  description = "The ID of the security group"
  value       = [for security_group_ecs_service_id in module.ecs-lb-service : security_group_ecs_service_id]
}

data "aws_security_groups" "ecs_sg_list" {
  filter {
    name   = "vpc-id"
    values = [local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id]
  }
  filter {
    name   = "group-name"
    values = ["${local.env.environment}-${var.project}-*-sg-service", "${local.env.environment}-${var.project}-*-sg-alb"]
  }
  depends_on = [module.ecs-service, module.ecs-lb-service]
}

module "vpc-endpoints" {
  source = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//vpcendpoint"
  vpcendpoints_interfaces = [
    {
      id                  = "ec2"
      vpc_id              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : module.vpc[0].private_subnets
      private_dns_enabled = true
      allowed_cidr_blocks = [local.env.cidr]
      inbound_ports       = [80, 443]
      tags = {
        Name        = "com.amazonaws.eu-west-1.ec2"
        Project     = var.project
        Environment = local.env.environment
      }
    },
    {
      id                  = "ec2messages"
      vpc_id              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : module.vpc[0].private_subnets
      private_dns_enabled = true
      allowed_cidr_blocks = [local.env.cidr]
      inbound_ports       = [80, 443]
      tags = {
        Name        = "com.amazonaws.eu-west-1.ec2"
        Project     = var.project
        Environment = local.env.environment
      }
    },
    {
      id                  = "ssm"
      vpc_id              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : module.vpc[0].private_subnets
      private_dns_enabled = true
      allowed_cidr_blocks = [local.env.cidr]
      inbound_ports       = [80, 443]
      tags = {
        Name        = "com.amazonaws.eu-west-1.ec2"
        Project     = var.project
        Environment = local.env.environment
      }
    },
    {
      id                  = "ssmmessages"
      vpc_id              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = local.env.environment == "prod" ? data.aws_subnets.ct_priv_subnets.ids : module.vpc[0].private_subnets
      private_dns_enabled = true
      allowed_cidr_blocks = [local.env.cidr]
      inbound_ports       = [80, 443]
      tags = {
        Name        = "com.amazonaws.eu-west-1.ec2"
        Project     = var.project
        Environment = local.env.environment
      }
    }
  ]
}

locals {
  associated_alb_arns = [for alb in module.ecs-lb-service : alb.alb_arn]
}

# waf
module "waf" {
  count                              = local.env.environment == "prod" ? 1 : 0
  source                             = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//waf"
  env                                = local.env.environment
  project_name                       = var.project
  waf_cloudfront_enable              = false
  waf_regional_enable                = true
  logs_enable                        = true
  logs_s3_enable                     = false
  logs_retension                     = 30
  regional_rule                      = "${local.env.environment}-${var.project}-ip-set"
  scope                              = "REGIONAL"
  associate_waf                      = true
  rate_limit                         = local.env.rate_limit
  rate_limit_exception_list          = local.env.rate_limit_exception_list
  managed_rule_group_statement_rules = null
}

resource "aws_wafv2_web_acl_association" "acl_alb_association" {
  for_each     = local.env.environment == "prod" ? local.env.enabled_nets : {}
  resource_arn = module.ecs-lb-service[each.key].alb_arn
  web_acl_arn  = try(module.waf[0].waf_limiter_arn, "arn:aws:wafv2:eu-west-1:905964754131:regional/webacl/prod-veworld-web-acl/dd94af78-8e6b-4a88-aa18-dc5cae4a325e")
}

# enable ebs-snapshot lambda on dev env only
# module "ebs-snapshot" {
#   source                                = "git::git@github.com:/vechainfoundation/devops.git//ebs_snapshot"
#   count                                 = local.env.environment == "dev" ? 1 : 0
#   region                                = local.env.region
#   environment                           = local.env.environment
#   snapshot_retention_days               = 7
#   snapshot_creation_schedule_expression = "cron(0 23 ? * 5 *)"
#   snapshot_deletion_schedule_expression = "cron(0 22 ? * 5 *)"
# }
