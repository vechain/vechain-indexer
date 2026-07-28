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
  depends_on                        = [module.ecs-cluster, resource.aws_security_group.ecs_service_sg, resource.aws_security_group.alb-sg]
  for_each                          = local.env.enabled_nets
  source                            = "./modules/ecs-loadbalanced-webservice"
  ssl_policy                        = "ELBSecurityPolicy-TLS-1-2-2017-01"
  region                            = local.env.region
  vpc_id                            = data.terraform_remote_state.vpc.outputs.vpc_id
  cluster_name                      = module.ecs-cluster.name
  autoscale_cluster_name            = module.ecs-cluster.name
  lb_subnets                        = data.terraform_remote_state.vpc.outputs.public_subnets
  app_subnets                       = data.terraform_remote_state.vpc.outputs.private_subnets
  env                               = local.env.environment
  is_create_repo                    = false
  secrets_enable                    = false
  assign_public_ip                  = false
  ecr_repo_uri                      = each.value.api.ecr_common_repo
  app_name                          = "${each.key}-api"
  ecr_image_tag                     = each.value.image_version
  project                           = var.project
  cpu                               = each.value.api.cpu
  memory                            = each.value.api.memory
  cidr                              = local.env.cidr
  container_port                    = 8080
  certificate_arn                   = local.env.certificate_arn
  ecs_sg                            = [aws_security_group.alb-sg.id]
  rule_0_path_pattern               = ["/api/v*", "/api-docs", "/api-docs/*", "/swagger-ui/*"]
  alb_sg                            = [aws_security_group.alb-sg.id]
  namespace_id                      = aws_service_discovery_private_dns_namespace.ns.id
  https_tg_healthcheck_path         = "/actuator/health"
  health_check_grace_period_seconds = 300
  healthcheck = {
    command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/liveness"]
    start_delay = 120
  }
  additional_containers = local.observability_sidecar_enabled ? [module.observability_sidecar_api[each.key].container_definition] : []
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
      value = each.value.api.logging.app-log-level
    },
    {
      name  = "TIMING_WARN_THRESHOLD_MS"
      value = each.value.api.timing.warn-threshold-ms
    },
    {
      name  = "TIMING_VERY_SLOW_THRESHOLD_MS"
      value = each.value.api.timing.very-slow-threshold-ms
    },
    {
      name  = "CACHE_MAX_SIZE"
      value = each.value.api.cache.max-size
    },
    {
      name  = "CACHE_TTL_SECONDS"
      value = each.value.api.cache.ttl-seconds
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ENTITY_TYPE_MAX_SIZE"
      value = each.value.api.cache.user-all-time-count-by-entity-type.max-size
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ENTITY_TYPE_TTL_SECONDS"
      value = each.value.api.cache.user-all-time-count-by-entity-type.ttl-seconds
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.user-all-time-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.user-all-time-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.user-all-time-count-by-actions.max-size
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.user-all-time-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_APP_ID_MAX_SIZE"
      value = each.value.api.cache.app-all-time-count-by-app-id.max-size
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_APP_ID_TTL_SECONDS"
      value = each.value.api.cache.app-all-time-count-by-app-id.ttl-seconds
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.app-all-time-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.app-all-time-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.app-all-time-count-by-actions.max-size
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.app-all-time-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_MAX_SIZE"
      value = each.value.api.cache.app-daily-count.max-size
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_TTL_SECONDS"
      value = each.value.api.cache.app-daily-count.ttl-seconds
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.app-daily-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.app-daily-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.app-daily-count-by-actions.max-size
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.app-daily-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_MAX_SIZE"
      value = each.value.api.cache.app-round-count.max-size
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_TTL_SECONDS"
      value = each.value.api.cache.app-round-count.ttl-seconds
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.app-round-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.app-round-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.app-round-count-by-actions.max-size
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.app-round-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.user-daily-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.user-daily-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.user-daily-count-by-actions.max-size
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.user-daily-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ENTITY_TYPE_MAX_SIZE"
      value = each.value.api.cache.user-daily-count-by-entity-type.max-size
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ENTITY_TYPE_TTL_SECONDS"
      value = each.value.api.cache.user-daily-count-by-entity-type.ttl-seconds
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.user-round-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.user-round-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.user-round-count-by-actions.max-size
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.user-round-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ENTITY_TYPE_MAX_SIZE"
      value = each.value.api.cache.user-round-count-by-entity-type.max-size
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ENTITY_TYPE_TTL_SECONDS"
      value = each.value.api.cache.user-round-count-by-entity-type.ttl-seconds
    },
    {
      name  = "CACHE_OFFICIAL_TOKEN_ADDRESSES_MAX_SIZE"
      value = each.value.api.cache.official-token-addresses.max-size
    },
    {
      name  = "CACHE_OFFICIAL_TOKEN_ADDRESSES_TTL_SECONDS"
      value = each.value.api.cache.official-token-addresses.ttl-seconds
    },
    {
      name  = "CACHE_GM_NFT_COUNT_MAX_SIZE"
      value = each.value.api.cache.gm-nft-count.max-size
    },
    {
      name  = "CACHE_GM_NFT_COUNT_TTL_SECONDS"
      value = each.value.api.cache.gm-nft-count.ttl-seconds
    },
    {
      name  = "CACHE_B3TR_RICHLIST_TOTAL_HOLDERS_MAX_SIZE"
      value = each.value.api.cache.b3tr-richlist-total-holders.max-size
    },
    {
      name  = "CACHE_B3TR_RICHLIST_TOTAL_HOLDERS_TTL_SECONDS"
      value = each.value.api.cache.b3tr-richlist-total-holders.ttl-seconds
    },
    { name  = "THOR_URL"
      value = each.value.thor_url
    },
    {
      name  = "THOR_RATE_LIMIT_BYPASS_KEY"
      value = startswith(local.env.environment, "prod") ? data.aws_secretsmanager_secret_version.thor_rate_limit_bypass_token[0].secret_string : ""
    },
    {
      name  = "PRICE_ORACLE_THOR_URL"
      value = each.value.api.pricing.oracle.thor-url
    },
    {
      name  = "PRICE_ORACLE_CONTRACT"
      value = each.value.api.pricing.oracle.contract-address
    },
    {
      name  = "X_ALLOC_VOTING_CONTRACT"
      value = each.value.indexer.business-event.substitutions.X_ALLOC_VOTING_CONTRACT
    },
    {
      name  = "CHALLENGES_CONTRACT"
      value = each.value.indexer.business-event.substitutions.CHALLENGES_CONTRACT
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
    },
    {
      name  = "PROMETHEUS_METRICS_ENABLED"
      value = tostring(local.observability_sidecar_enabled)
    },
    {
      name  = "KNOWN_PROJECT_IDS"
      value = try(data.aws_secretsmanager_secret_version.known_project_ids[0].secret_string, "")
    }
  ]
  log_metric_filters = []

  ####### enable autoscailing #######
  # Pin to the autoscaling floor so applies never leave desired_count below min_capacity.
  desired_count                       = each.value.api.min_capacity
  enable_ecs_cpu_based_autoscaling    = true
  enable_ecs_memory_based_autoscaling = true
  min_capacity                        = each.value.api.min_capacity
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
  source                             = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs-backend-service?ref=v.3.2.1"
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
  health_check_grace_period_seconds  = 300
  additional_containers              = local.observability_sidecar_enabled ? [module.observability_sidecar_indexer[each.key].container_definition] : []
  extra_statements                   = local.observability_sidecar_enabled ? [module.observability_sidecar_indexer[each.key].amp_remote_write_statement] : []
  log_metric_filters                 = []
  healthcheck = {
    command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/liveness"]
    start_delay = 120
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
      name  = "THOR_RATE_LIMIT_BYPASS_KEY"
      value = startswith(local.env.environment, "prod") ? data.aws_secretsmanager_secret_version.thor_rate_limit_bypass_token[0].secret_string : ""
    },
    {
      name  = "APP_LOG_LEVEL"
      value = each.value.indexer.logging.app-log-level
    },
    {
      name  = "TIMING_LOG_LEVEL"
      value = each.value.indexer.logging.timing-log-level
    },
    {
      name  = "PRUNER_LOG_LEVEL"
      value = each.value.indexer.logging.pruner-log-level
    },
    {
      name  = "MONGO_DRIVER_LOG_LEVEL"
      value = each.value.indexer.logging.mongo-driver-log-level
    },
    {
      name  = "TIMING_WARN_THRESHOLD_MS"
      value = each.value.indexer.timing.warn-threshold-ms
    },
    {
      name  = "TIMING_VERY_SLOW_THRESHOLD_MS"
      value = each.value.indexer.timing.very-slow-threshold-ms
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
      name  = "INDEXER_START_BLOCK_NFTS"
      value = each.value.indexer.start-block.nfts
    },
    {
      name  = "INDEXER_START_BLOCK_NFT_BLACKLIST",
      value = each.value.indexer.start-block.nft-blacklist
    },
    {
      name  = "INDEXER_START_BLOCK_TRANSACTIONS"
      value = each.value.indexer.start-block.transactions
    },
    {
      name  = "INDEXER_START_BLOCK_TRANSFERS"
      value = each.value.indexer.start-block.transfers
    },
    {
      name  = "INDEXER_START_BLOCK_HISTORY"
      value = each.value.indexer.start-block.history
    },
    {
      name  = "INDEXER_START_BLOCK_VEVOTE"
      value = each.value.indexer.start-block.vevote
    },
    {
      name  = "INDEXER_START_BLOCK_STARGATE"
      value = each.value.indexer.start-block.stargate
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR"
      value = each.value.indexer.start-block.b3tr
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_CHALLENGES"
      value = each.value.indexer.start-block.b3tr-challenges
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_USER_CHALLENGES"
      value = each.value.indexer.start-block.b3tr-user-challenges
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_TREASURY"
      value = each.value.indexer.start-block.b3tr-treasury
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_NAVIGATOR"
      value = each.value.indexer.start-block.b3tr-navigator
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_BALANCE"
      value = each.value.indexer.start-block.b3tr-balance
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_PROPOSAL"
      value = each.value.indexer.start-block.b3tr-proposal
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_X_ALLOC_RESULT"
      value = each.value.indexer.start-block.b3tr-x-alloc-result
    },
    {
      name  = "INDEXER_START_BLOCK_B3TR_SUSTAINABLE_ACTIONS"
      value = each.value.indexer.start-block.b3tr-sustainable-actions
    },
    {
      name  = "INDEXER_START_BLOCK_HISTORIC_PROPOSALS"
      value = each.value.indexer.start-block.historic-proposals
    },
    {
      name  = "INDEXER_START_BLOCK_VALIDATOR"
      value = each.value.indexer.start-block.validator
    },
    {
      name  = "INDEXER_START_BLOCK_DELEGATION"
      value = each.value.indexer.start-block.delegation
    },
    {
      name  = "INDEXER_START_BLOCK_VTHO_GENERATED_BY_BLOCK"
      value = each.value.indexer.start-block.vtho-generated-by-block
    },
    {
      name  = "INDEXER_START_BLOCK_VTHO_CLAIMED_BY_BLOCK"
      value = each.value.indexer.start-block.vtho-claimed-by-block
    },
    {
      name  = "INDEXER_START_BLOCK_VET_STAKED_BY_BLOCK"
      value = each.value.indexer.start-block.vet-staked-by-block
    },
    {
      name  = "INDEXER_START_BLOCK_VET_DELEGATED_BY_BLOCK"
      value = each.value.indexer.start-block.vet-delegated-by-block
    },
    {
      name  = "INDEXER_START_BLOCK_NFT_HOLDERS_BY_BLOCK"
      value = each.value.indexer.start-block.nft-holders-by-block
    },
    {
      name  = "INDEXER_START_BLOCK_NFT_OWNER_BALANCE"
      value = each.value.indexer.start-block.nft-owner-balance
    },
    {
      name  = "INDEXER_START_BLOCK_ACCOUNT_TOTALS_SERIES"
      value = each.value.indexer.start-block.account-totals-series
    },
    {
      name  = "INDEXER_START_BLOCK_AVERAGE_FEES_PER_USER"
      value = each.value.indexer.start-block.average-fees-per-user
    },
    {
      name  = "INDEXER_START_BLOCK_SAFE_PROXIES"
      value = each.value.indexer.start-block.safe-proxies
    },
    {
      name  = "INDEXER_START_BLOCK_SAFE_MEMBERSHIP"
      value = each.value.indexer.start-block.safe-membership
    },
    {
      name  = "INDEXER_START_BLOCK_SAFE_TX_STATE"
      value = each.value.indexer.start-block.safe-tx-state
    },
    {
      name  = "INDEXER_START_BLOCK_SAFE_TX_PROPOSALS"
      value = each.value.indexer.start-block.safe-tx-proposals
    },
    {
      name  = "BLACKLIST_CONTRACT_ADDRESS"
      value = each.value.indexer.blacklist.contract-address
    },
    {
      name  = "STEERING_COMMITTEE_ADDRESS"
      value = each.value.veworld.contract.historic-proposals.steering-committee
    },
    {
      name  = "ALL_STAKEHOLDERS_ADDRESS"
      value = each.value.veworld.contract.historic-proposals.all-stakeholders
    },
    {
      name  = "CACHE_MAX_SIZE"
      value = each.value.api.cache.max-size
    },
    {
      name  = "CACHE_TTL_SECONDS"
      value = each.value.api.cache.ttl-seconds
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ENTITY_TYPE_MAX_SIZE"
      value = each.value.api.cache.user-all-time-count-by-entity-type.max-size
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ENTITY_TYPE_TTL_SECONDS"
      value = each.value.api.cache.user-all-time-count-by-entity-type.ttl-seconds
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.user-all-time-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.user-all-time-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.user-all-time-count-by-actions.max-size
    },
    {
      name  = "CACHE_USER_ALL_TIME_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.user-all-time-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_APP_ID_MAX_SIZE"
      value = each.value.api.cache.app-all-time-count-by-app-id.max-size
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_APP_ID_TTL_SECONDS"
      value = each.value.api.cache.app-all-time-count-by-app-id.ttl-seconds
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.app-all-time-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.app-all-time-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.app-all-time-count-by-actions.max-size
    },
    {
      name  = "CACHE_APP_ALL_TIME_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.app-all-time-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_MAX_SIZE"
      value = each.value.api.cache.app-daily-count.max-size
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_TTL_SECONDS"
      value = each.value.api.cache.app-daily-count.ttl-seconds
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.app-daily-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.app-daily-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.app-daily-count-by-actions.max-size
    },
    {
      name  = "CACHE_APP_DAILY_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.app-daily-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_MAX_SIZE"
      value = each.value.api.cache.app-round-count.max-size
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_TTL_SECONDS"
      value = each.value.api.cache.app-round-count.ttl-seconds
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.app-round-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.app-round-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.app-round-count-by-actions.max-size
    },
    {
      name  = "CACHE_APP_ROUND_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.app-round-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.user-daily-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.user-daily-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.user-daily-count-by-actions.max-size
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.user-daily-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ENTITY_TYPE_MAX_SIZE"
      value = each.value.api.cache.user-daily-count-by-entity-type.max-size
    },
    {
      name  = "CACHE_USER_DAILY_COUNT_BY_ENTITY_TYPE_TTL_SECONDS"
      value = each.value.api.cache.user-daily-count-by-entity-type.ttl-seconds
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_TOTAL_REWARD_MAX_SIZE"
      value = each.value.api.cache.user-round-count-by-total-reward.max-size
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_TOTAL_REWARD_TTL_SECONDS"
      value = each.value.api.cache.user-round-count-by-total-reward.ttl-seconds
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ACTIONS_MAX_SIZE"
      value = each.value.api.cache.user-round-count-by-actions.max-size
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ACTIONS_TTL_SECONDS"
      value = each.value.api.cache.user-round-count-by-actions.ttl-seconds
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ENTITY_TYPE_MAX_SIZE"
      value = each.value.api.cache.user-round-count-by-entity-type.max-size
    },
    {
      name  = "CACHE_USER_ROUND_COUNT_BY_ENTITY_TYPE_TTL_SECONDS"
      value = each.value.api.cache.user-round-count-by-entity-type.ttl-seconds
    },
    {
      name  = "CACHE_OFFICIAL_TOKEN_ADDRESSES_MAX_SIZE"
      value = each.value.api.cache.official-token-addresses.max-size
    },
    {
      name  = "CACHE_OFFICIAL_TOKEN_ADDRESSES_TTL_SECONDS"
      value = each.value.api.cache.official-token-addresses.ttl-seconds
    },
    {
      name  = "CACHE_GM_NFT_COUNT_MAX_SIZE"
      value = each.value.api.cache.gm-nft-count.max-size
    },
    {
      name  = "CACHE_GM_NFT_COUNT_TTL_SECONDS"
      value = each.value.api.cache.gm-nft-count.ttl-seconds
    },
    {
      name  = "CACHE_B3TR_RICHLIST_TOTAL_HOLDERS_MAX_SIZE"
      value = each.value.api.cache.b3tr-richlist-total-holders.max-size
    },
    {
      name  = "CACHE_B3TR_RICHLIST_TOTAL_HOLDERS_TTL_SECONDS"
      value = each.value.api.cache.b3tr-richlist-total-holders.ttl-seconds
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
      name  = "VERSION_FUNGIBLE_TOKEN_INTERACTIONS"
      value = each.value.indexer.version.fungible-token-interactions
    },
    {
      name  = "VERSION_TRANSACTIONS"
      value = each.value.indexer.version.transactions
    },
    {
      name  = "VERSION_TRANSACTION_COUNT"
      value = each.value.indexer.version.transaction-count
    },
    {
      name  = "VERSION_HISTORY"
      value = each.value.indexer.version.history
    },
    {
      name  = "VERSION_NFT_BLACKLIST"
      value = each.value.indexer.version.nft-blacklist
    },
    {
      name  = "VERSION_VEVOTE_COMMENTS"
      value = each.value.indexer.version.vevote-comments
    },
    {
      name  = "VERSION_VEVOTE_RESULTS"
      value = each.value.indexer.version.vevote-results
    },
    {
      name  = "VERSION_STARGATE_VTHO_CLAIMED_BY_BLOCK"
      value = each.value.indexer.version.stargate-vtho-claimed-by-block
    },
    {
      name  = "VERSION_STARGATE_VTHO_GENERATED_BY_BLOCK"
      value = each.value.indexer.version.stargate-vtho-generated-by-block
    },
    {
      name  = "VERSION_STARGATE_VTHO_CLAIMED_BY_ACCOUNT"
      value = each.value.indexer.version.stargate-vtho-claimed-by-account
    },
    {
      name  = "VERSION_STARGATE_VET_STAKED_BY_BLOCK"
      value = each.value.indexer.version.stargate-vet-staked-by-block
    },
    {
      name  = "VERSION_STARGATE_NFT_HOLDERS_BY_BLOCK"
      value = each.value.indexer.version.stargate-nft-holders-by-block
    },
    {
      name  = "VERSION_STARGATE_NFT_OWNER_BALANCE"
      value = each.value.indexer.version.stargate-nft-owner-balance
    },
    {
      name  = "VERSION_STARGATE_VET_DELEGATED_BY_BLOCK"
      value = each.value.indexer.version.stargate-vet-delegated-by-block
    },
    {
      name  = "VERSION_HISTORIC_PROPOSALS"
      value = each.value.indexer.version.historic-proposals
    },
    {
      name  = "VERSION_B3TR_PROPOSAL_COMMENTS"
      value = each.value.indexer.version.b3tr-proposal-comments
    },
    {
      name  = "VERSION_B3TR_PROPOSAL_RESULTS"
      value = each.value.indexer.version.b3tr-proposal-results
    },
    {
      name  = "VERSION_B3TR_CHALLENGES"
      value = each.value.indexer.version.b3tr-challenges
    },
    {
      name  = "VERSION_B3TR_USER_CHALLENGES"
      value = each.value.indexer.version.b3tr-user-challenges
    },
    {
      name  = "VERSION_B3TR_USER_ALL_TIME_ACTION_SUMMARY"
      value = each.value.indexer.version.b3tr-user-all-time-action-summary
    },
    {
      name  = "VERSION_B3TR_APP_ALL_TIME_ACTION_SUMMARY"
      value = each.value.indexer.version.b3tr-app-all-time-action-summary
    },
    {
      name  = "VERSION_B3TR_APP_ROUND_ACTION_SUMMARY"
      value = each.value.indexer.version.b3tr-app-round-action-summary
    },
    {
      name  = "VERSION_B3TR_APP_DAILY_ACTION_SUMMARY"
      value = each.value.indexer.version.b3tr-app-daily-action-summary
    },
    {
      name  = "VERSION_B3TR_USER_DAILY_ACTION_SUMMARY"
      value = each.value.indexer.version.b3tr-user-daily-action-summary
    },
    {
      name  = "VERSION_B3TR_USER_ROUND_ACTION_SUMMARY"
      value = each.value.indexer.version.b3tr-user-round-action-summary
    },
    {
      name  = "VERSION_B3TR_X_ALLOC_RESULT"
      value = each.value.indexer.version.b3tr-x-alloc-result
    },
    {
      name  = "VERSION_B3TR_TREASURY"
      value = each.value.indexer.version.b3tr-treasury
    },
    {
      name  = "VERSION_B3TR_GM_NFT"
      value = each.value.indexer.version.b3tr-gm-nft
    },
    {
      name  = "VERSION_B3TR_GM_NFT_LEVEL_OVERVIEW"
      value = each.value.indexer.version.b3tr-gm-nft-level-overview
    },
    {
      name  = "VERSION_BLOCK_USAGE"
      value = each.value.indexer.version.block-usage
    },
    {
      name  = "VERSION_AVERAGE_FEES_PER_USER"
      value = each.value.indexer.version.average-fees-per-user
    },
    {
      name  = "VERSION_VALIDATOR"
      value = each.value.indexer.version.validator
    },
    {
      name  = "VERSION_VALIDATOR_BLOCKS"
      value = each.value.indexer.version.validator-rewards
    },
    {
      name  = "VERSION_STARGATE_TOKEN"
      value = each.value.indexer.version.stargate-token
    },
    {
      name  = "VERSION_TOKEN_REWARDS"
      value = each.value.indexer.version.token-rewards
    },
    {
      name  = "VERSION_DELEGATION"
      value = each.value.indexer.version.delegation
    },
    {
      name  = "VERSION_ACCOUNT_TOTALS_SERIES"
      value = each.value.indexer.version.account-totals-series
    },
    {
      name  = "VERSION_ACCOUNT_OVERVIEW"
      value = each.value.indexer.version.account-overview
    },
    {
      name  = "VERSION_B3TR_BALANCE"
      value = each.value.indexer.version.b3tr-balance
    },
    {
      name  = "VERSION_CONTRACTS"
      value = each.value.indexer.version.contracts
    },
    {
      name  = "VERSION_VET_BALANCE"
      value = each.value.indexer.version.vet-balance
    },
    {
      name  = "MIN_COMMENT_LEN"
      value = each.value.comments.min-length
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
      name  = "EMISSIONS_CONTRACT"
      value = each.value.indexer.business-event.substitutions.EMISSIONS_CONTRACT
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
      name  = "B3TR_DBA_POOL_CONTRACT"
      value = each.value.indexer.business-event.substitutions.B3TR_DBA_POOL_CONTRACT
    },
    {
      name  = "GM_NFT_CONTRACT"
      value = each.value.indexer.business-event.substitutions.GM_NFT_CONTRACT
    },
    {
      name  = "X_ALLOC_VOTING_CONTRACT"
      value = each.value.indexer.business-event.substitutions.X_ALLOC_VOTING_CONTRACT
    },
    {
      name  = "CHALLENGES_CONTRACT"
      value = each.value.indexer.business-event.substitutions.CHALLENGES_CONTRACT
    },
    {
      name  = "NODE_MANAGEMENT_CONTRACT"
      value = each.value.indexer.business-event.substitutions.NODE_MANAGEMENT_CONTRACT
    },
    {
      name  = "X_ALLOC_POOL_CONTRACT"
      value = each.value.indexer.business-event.substitutions.X_ALLOC_POOL_CONTRACT
    },
    {
      name  = "X2EARN_REWARDS_POOL_CONTRACT"
      value = each.value.indexer.business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT
    },
    {
      name  = "VOTER_REWARDS_CONTRACT"
      value = each.value.indexer.business-event.substitutions.VOTER_REWARDS_CONTRACT
    },
    {
      name  = "TREASURY_CONTRACT"
      value = each.value.indexer.business-event.substitutions.TREASURY_CONTRACT
    },
    {
      name  = "STARGATE_DELEGATION_CONTRACT"
      value = each.value.indexer.business-event.substitutions.STARGATE_DELEGATION_CONTRACT
    },
    {
      name  = "STARGATE_NFT_CONTRACT"
      value = each.value.indexer.business-event.substitutions.STARGATE_NFT_CONTRACT
    },
    {
      name  = "RELAYER_REWARDS_POOL_CONTRACT"
      value = each.value.indexer.business-event.substitutions.RELAYER_REWARDS_POOL_CONTRACT
    },
    {
      name  = "NAVIGATOR_REGISTRY_CONTRACT"
      value = each.value.indexer.business-event.substitutions.NAVIGATOR_REGISTRY_CONTRACT
    },
    {
      name  = "VEVOTE_CONTRACT"
      value = each.value.indexer.business-event.substitutions.VEVOTE_CONTRACT
    },
    {
      name  = "STARGATE_CONTRACT"
      value = each.value.indexer.business-event.substitutions.STARGATE_CONTRACT
    },
    {
      name  = "BUILTIN_STAKER_CONTRACT"
      value = each.value.indexer.business-event.substitutions.BUILTIN_STAKER_CONTRACT
    },
    {
      name  = "GET_ALL_VALIDATORS_CONTRACT"
      value = each.value.indexer.business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT
    },
    {
      name  = "SAFE_PROXY_FACTORY_CONTRACT"
      value = each.value.indexer.business-event.substitutions.SAFE_PROXY_FACTORY_CONTRACT
    },
    {
      name  = "SAFE_EMITTER_CONTRACT"
      value = each.value.indexer.business-event.substitutions.SAFE_EMITTER_CONTRACT
    },
    {
      name  = "INDEXER_SYNC_LOG_INTERVAL"
      value = each.value.indexer.sync-log-interval
    },
    {
      name  = "INDEXER_CHANNEL_BATCH_SIZE"
      value = each.value.indexer.channel-batch-size
    },
    {
      name  = "INDEXER_CHECKPOINT_SAVE_INTERVAL_SECONDS"
      value = each.value.indexer.checkpoint-save-interval-seconds
    },
    {
      name  = "HEALTHCHECK_INACTIVE_THRESHOLD_SYNCING"
      value = each.value.indexer.healthcheck.inactive-threshold-syncing
    },
    {
      name  = "HEALTHCHECK_INACTIVE_THRESHOLD_NOT_SYNCING"
      value = each.value.indexer.healthcheck.inactive-threshold-not-syncing
    },
    {
      name  = "HEALTHCHECK_REPORT_INTERVAL_MS"
      value = each.value.indexer.healthcheck.report-interval-ms
    },
    {
      name  = "METRICS_ID"
      value = "${local.env.environment}-${each.key}"
    },
    {
      name  = "DEPLOYMENT_SCOPE"
      value = "${local.env.environment}-${each.key}"
    }
  ]
}

################################################################################
# WAF Rate Limit Bypass Token
# Shared across blue/green — first color to deploy creates the secret,
# subsequent deploys read the existing one.
################################################################################

locals {
  waf_bypass_secret_name = "/prod/${var.project}/waf-rate-limit-bypass-token"
}

resource "null_resource" "ensure_waf_bypass_secret" {
  count = startswith(local.env.environment, "prod") ? 1 : 0

  triggers = {
    secret_name = local.waf_bypass_secret_name
  }

  provisioner "local-exec" {
    command = <<-EOT
      aws secretsmanager describe-secret --secret-id "${local.waf_bypass_secret_name}" --region ${local.env.region} 2>/dev/null || \
      aws secretsmanager create-secret \
        --name "${local.waf_bypass_secret_name}" \
        --secret-string "$(openssl rand -hex 32)" \
        --region ${local.env.region}
    EOT
  }
}

data "aws_secretsmanager_secret_version" "waf_rate_limit_bypass_token" {
  count      = startswith(local.env.environment, "prod") ? 1 : 0
  secret_id  = local.waf_bypass_secret_name
  depends_on = [null_resource.ensure_waf_bypass_secret]
}

################################################################################
# Known Project IDs
# Comma-separated whitelist of X-Project-Id header values recognised by the API,
# kept out of the repo so the caller list isn't public. Shared across blue/green.
################################################################################

locals {
  known_project_ids_secret_name = "/prod/${var.project}/known-project-ids"
}

resource "null_resource" "ensure_known_project_ids_secret" {
  count = startswith(local.env.environment, "prod") ? 1 : 0

  triggers = {
    secret_name = local.known_project_ids_secret_name
  }

  provisioner "local-exec" {
    command = <<-EOT
      aws secretsmanager describe-secret --secret-id "${local.known_project_ids_secret_name}" --region ${local.env.region} 2>/dev/null || \
      aws secretsmanager create-secret \
        --name "${local.known_project_ids_secret_name}" \
        --secret-string "" \
        --region ${local.env.region}
    EOT
  }
}

data "aws_secretsmanager_secret_version" "known_project_ids" {
  count      = startswith(local.env.environment, "prod") ? 1 : 0
  secret_id  = local.known_project_ids_secret_name
  depends_on = [null_resource.ensure_known_project_ids_secret]
}

################################################################################
# Thor Upstream Rate Limit Bypass Token
# Secret value is provided by the Thor node operator out-of-band and must be
# pre-created in AWS Secrets Manager before applying this stack.
################################################################################
locals {
  thor_rate_limit_bypass_secret_name = "/prod/${var.project}/thor-rate-limit-bypass-token"
}

data "aws_secretsmanager_secret_version" "thor_rate_limit_bypass_token" {
  count     = startswith(local.env.environment, "prod") ? 1 : 0
  secret_id = local.thor_rate_limit_bypass_secret_name
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

################################################################################
# WAF for API Load Balancers
################################################################################

module "waf" {
  count  = startswith(local.env.environment, "prod") ? 1 : 0
  source = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//waf?ref=v.3.2.0"

  env          = local.env.environment
  project_name = "${var.project}-indexer"
  scope        = "REGIONAL"

  # Enable regional WAF for ALB protection
  waf_regional_enable = true
  logs_enable         = true
  logs_retension      = 30

  # Associate WAF with all API ALBs
  associate_waf = true
  resource_arn  = [for service in module.ecs-lb-service-api : service.alb_arn]

  # Rate limiting configuration (defaults to 2000 requests per 5 minutes per IP)
  rate_limit                     = local.env.alb.waf.waf_rate_limit
  rate_limit_exception_list      = local.env.alb.waf.waf_rate_limit_exception_list
  rate_limit_bypass_header_name  = length(data.aws_secretsmanager_secret_version.waf_rate_limit_bypass_token) > 0 ? lookup(local.env.alb.waf, "waf_rate_limit_bypass_header_name", "") : ""
  rate_limit_bypass_header_value = try(data.aws_secretsmanager_secret_version.waf_rate_limit_bypass_token[0].secret_string, "")

  # Required variables
  managed_rule_group_statement_rules = []
  rate_based_statement_rules         = []

  depends_on = [module.ecs-lb-service-api]
}
