variable "project" {
  default = "veworld"
}

variable "app_name" {
  default = ""
}

resource "aws_service_discovery_private_dns_namespace" "ns" {
  name = "${local.env.environment}.${var.project}"
  vpc  = data.terraform_remote_state.vpc.outputs.vpc_id
}

######################
# ALB Security Group
######################

resource "aws_security_group" "alb-sg" {
  description = "security-group-alb"
  name        = "${local.env.environment}-${var.project}-sg-alb"
  egress {
    cidr_blocks = ["0.0.0.0/0"]
    from_port   = 0
    protocol    = "-1"
    to_port     = 0
  }

  ingress {
    cidr_blocks = ["0.0.0.0/0"]
    from_port   = 80
    protocol    = "tcp"
    to_port     = 80
  }

  ingress {
    cidr_blocks = ["0.0.0.0/0"]
    from_port   = 443
    protocol    = "tcp"
    to_port     = 443
  }

  ingress {
    from_port = 0
    protocol  = "-1"
    to_port   = 0
    self      = true
  }

  tags = {
    Environment = local.env.environment
    Name        = "${local.env.environment}-${var.project}-sg-alb"
  }
  vpc_id = data.terraform_remote_state.vpc.outputs.vpc_id
}

######################
# ECS Service Security Group
######################

resource "aws_security_group" "ecs_service_sg" {
  description = "security-group-service"

  name = "${local.env.environment}-${var.project}-${var.app_name}-sg-service"
  egress {
    cidr_blocks = ["0.0.0.0/0"]
    from_port   = 0
    protocol    = "-1"
    to_port     = 0
  }
  ingress {
    from_port = 0
    protocol  = "-1"
    to_port   = 0
    self      = true
  }
  tags = {
    Environment = local.env.environment
    Name        = "${local.env.environment}-${var.project}-${var.app_name}-sg-service"
  }

  vpc_id = data.terraform_remote_state.vpc.outputs.vpc_id
}

################################################################################
# Module For ECS Cluster creation
################################################################################

module "ecs-cluster" {
  source  = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs_cluster?ref=v.3.1.8"
  env     = local.env.environment
  project = var.project
  vpc_id  = data.terraform_remote_state.vpc.outputs.vpc_id
  cidr    = local.env.cidr
}

################################################################################
# Module For ECS Load Balanced Service API
################################################################################

module "ecs-lb-service-api" {
  depends_on                = [module.ecs-cluster, resource.aws_security_group.ecs_service_sg, resource.aws_security_group.alb-sg]
  for_each                  = local.env.enabled_nets
  source                    = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs-loadbalanced-webservice?ref=v.1.0.66"
  ssl_policy                = "ELBSecurityPolicy-TLS-1-2-2017-01"
  region                    = local.env.region
  vpc_id                    = data.terraform_remote_state.vpc.outputs.vpc_id
  cluster_name              = module.ecs-cluster.name
  autoscale_cluster_name    = module.ecs-cluster.name
  lb_subnets                = data.terraform_remote_state.vpc.outputs.public_subnets
  app_subnets               = data.terraform_remote_state.vpc.outputs.private_subnets
  env                       = local.env.environment
  is_create_repo            = false
  secrets_enable            = false
  assign_public_ip          = false
  ecr_repo_uri              = each.value.api.ecr_common_repo
  app_name                  = "${each.key}-api"
  ecr_image_tag             = each.value.image_version
  project                   = var.project
  cpu                       = each.value.api.cpu
  memory                    = each.value.api.memory
  cidr                      = local.env.cidr
  container_port            = 8080
  certificate_arn           = local.env.certificate_arn
  ecs_sg                    = [aws_security_group.alb-sg.id]
  rule_0_path_pattern       = ["/api/v*", "/api-docs", "/swagger-ui/*"]
  alb_sg                    = [aws_security_group.alb-sg.id]
  namespace_id              = aws_service_discovery_private_dns_namespace.ns.id
  https_tg_healthcheck_path = "/actuator/health"
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
      value = format("%s://api-${local.env.environment}:%s@%s/vechain?%s&readPreference=secondary", each.value.mongodb.proto, urlencode(aws_secretsmanager_secret_version.api_db_user_secret_version.secret_string), "${local.env.environment}-${each.value.mongodb.fqdn}", each.value.mongodb.opts)
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
  log_metric_filters = [for filter in each.value.api.log_metric_filters : {
    name    = filter.name
    pattern = filter.pattern
  }]

  ####### enable autoscailing #######
  enable_ecs_cpu_based_autoscaling    = true
  enable_ecs_memory_based_autoscaling = true
  min_capacity                        = 1
  max_capacity                        = each.value.api.max_capacity
  target_cpu_value                    = 70
  target_memory_value                 = 70
  disable_scale_in                    = false
  # scale_in_cooldown = 300
  # scale_out_cooldown = 300
  name = "auto-scaling-group"
}

################################################################################
# Module For ECS Non-Load Balanced Service API
################################################################################
module "ecs-backend-service" {
  depends_on                         = [module.ecs-cluster]
  for_each                           = local.env.enabled_nets
  source                             = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs-backend-service?ref=v.3.0.3"
  vpc_id                             = data.terraform_remote_state.vpc.outputs.vpc_id
  region                             = local.env.region
  cluster                            = module.ecs-cluster.name
  subnets                            = concat(data.terraform_remote_state.vpc.outputs.private_subnets)
  env                                = local.env.environment
  is_create_repo                     = false
  secrets_enable                     = false
  ecr_repo_uri                       = each.value.indexer.ecr_common_repo
  ecr_image_tag                      = each.value.image_version
  app_name                           = "${each.key}-indexer"
  project                            = var.project
  cpu                                = each.value.indexer.cpu
  memory                             = each.value.indexer.memory
  cidr                               = local.env.cidr
  security_groups                    = [aws_security_group.ecs_service_sg.id]
  desired_capacity                   = each.value.indexer.enabled ? 1 : 0
  containerPort                      = 8080
  hostPort                           = 8080
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100
  namespace_id                       = aws_service_discovery_private_dns_namespace.ns.id
  log_metric_filters = [for filter in each.value.indexer.log_metric_filters : {
    name    = filter.name
    pattern = filter.pattern
  }]
  healthcheck = {
    command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health"]
    start_delay = 30
  }
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
      value = format("%s://indexer-${local.env.environment}:%s@%s/vechain?%s", each.value.mongodb.proto, urlencode(aws_secretsmanager_secret_version.indexer_db_user_secret_version.secret_string), "${local.env.environment}-${each.value.mongodb.fqdn}", each.value.mongodb.opts)
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
      name  = "SLACK_WEBHOOK_URL"
      value = each.value.indexer.slack_webhook_url
    },
    {
      name  = "INDEXER_START_BLOCK_NFTS"
      value = each.value.indexer.start_block.nfts
    },
    {
      name  = "INDEXER_START_BLOCK_NFT_BLACKLIST",
      value = each.value.indexer.start_block.nft_blacklist
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
      name  = "INDEXER_START_BLOCK_HISTORY"
      value = each.value.indexer.start_block.history
    },
    {
      name  = "INDEXER_START_BLOCK_VEVOTE"
      value = each.value.indexer.start_block.vevote
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
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_HISTORY"
      value = each.value.indexer.sync_logger_interval.history
    },
    {
      name  = "INDEXER_SYNC_LOGGER_INTERVAL_VEVOTE"
      value = each.value.indexer.sync_logger_interval.vevote
    },
    {
      name  = "PRUNER_ENABLED"
      value = each.value.indexer.pruner.enabled
    },
    {
      name  = "PRUNER_INTERVAL"
      value = each.value.indexer.pruner.interval
    },
    {
      name  = "PRUNER_INITIAL_DELAY"
      value = each.value.indexer.pruner.initial_delay
    },
    {
      name  = "PRUNER_REMOVAL_CHUNK_SIZE"
      value = each.value.indexer.pruner.removal_chunk_size
    },
    {
      name  = "BLACKLIST_CONTRACT_ADDRESS"
      value = each.value.indexer.blacklist.contract_address
    },
    {
      name  = "VEVOTE_CONTRACT"
      value = each.value.veworld.contract.vevote.address
    },
    {
      name  = "BLACKLIST_INTERVAL"
      value = each.value.indexer.blacklist.interval
    },
    {
      name  = "BLACKLIST_INITIAL_DELAY"
      value = each.value.indexer.blacklist.initial_delay
    },
    {
      name  = "VERSION_NFTS"
      value = each.value.indexer.version.nfts
    },
    {
      name  = "VERSION_TRANSFERS"
      value = each.value.indexer.version.transfers
    },
    {
      name  = "VERSION_TRANSACTIONS"
      value = each.value.indexer.version.transactions
    },
    {
      name  = "VERSION_HISTORY"
      value = each.value.indexer.version.history
    },
    {
      name  = "VERSION_NFT_BLACKLIST"
      value = each.value.indexer.version.nft_blacklist
    },
    {
      name  = "VERSION_VEVOTE_COMMENTS"
      value = each.value.indexer.version.vevote_comments
    },
    {
      name  = "VERSION_VEVOTE_RESULTS"
      value = each.value.indexer.version.vevote_results
    },
    {
      name  = "MIN_COMMENT_LEN"
      value = each.value.comments.minLength
    },
    {
      name  = "LANGUAGE_CONFIDENCE"
      value = each.value.comments.language.confidence
    },
    {
      name  = "B3TR_CONTRACT"
      value = each.value.indexer.business-event.substitutions.B3TR_CONTRACT
    },
    {
      name  = "VOT3_CONTRACT"
      value = each.value.indexer.business-event.substitutions.VOT3_CONTRACT
    },
    {
      name  = "B3TR_GOVERNOR_CONTRACT"
      value = each.value.indexer.business-event.substitutions.B3TR_GOVERNOR_CONTRACT
    },
    {
      name  = "GM_NFT_CONTRACT"
      value = each.value.indexer.business-event.substitutions.GM_NFT_CONTRACT
    },
    {
      name = "X_ALLOC_VOTING_CONTRACT"
      value = each.value.indexer.business-event.substitutions.X_ALLOC_VOTING_CONTRACT
    },
    {
      name = "X2EARN_REWARDS_POOL_CONTRACT"
      value = each.value.indexer.business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT
    },
    {
      name  = "VOTER_REWARDS_CONTRACT"
      value = each.value.indexer.business-event.substitutions.VOTER_REWARDS_CONTRACT
    },
    {
      name  = "TREASURY_ADDRESS"
      value = each.value.indexer.business-event.substitutions.TREASURY_ADDRESS
    }
  ]
}

data "aws_security_groups" "ecs_sg_list" {
  filter {
    name   = "vpc-id"
    values = [data.terraform_remote_state.vpc.outputs.vpc_id]
  }
  filter {
    name   = "group-name"
    values = ["${local.env.environment}-${var.project}-*-sg-service", "${local.env.environment}-${var.project}-*-sg-alb"]
  }
}

module "vpc-endpoints" {
  source = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//vpcendpoint?ref=v.1.0.19"
  vpcendpoints_interfaces = local.env.environment == "dev" ? [
    {
      id                  = "ec2"
      vpc_id              = data.terraform_remote_state.vpc.outputs.vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = data.terraform_remote_state.vpc.outputs.private_subnets
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
      vpc_id              = data.terraform_remote_state.vpc.outputs.vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = data.terraform_remote_state.vpc.outputs.private_subnets
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
      vpc_id              = data.terraform_remote_state.vpc.outputs.vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = data.terraform_remote_state.vpc.outputs.private_subnets
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
      vpc_id              = data.terraform_remote_state.vpc.outputs.vpc_id
      security_group_ids  = data.aws_security_groups.ecs_sg_list.ids
      subnet_ids          = data.terraform_remote_state.vpc.outputs.private_subnets
      private_dns_enabled = true
      allowed_cidr_blocks = [local.env.cidr]
      inbound_ports       = [80, 443]
      tags = {
        Name        = "com.amazonaws.eu-west-1.ec2"
        Project     = var.project
        Environment = local.env.environment
      }
    }
  ] : []
}

# waf
module "waf" {
  count                              = startswith(local.env.environment, "prod") ? 1 : 0
  source                             = "git::git@github.com:/vechain/devops.git//waf?ref=release/node-hosting/v6"
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
