data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

data "aws_region" "current" {}



resource "aws_cloudwatch_log_group" "ecs_cw_log_group" {
  name              = lower("${var.project}-${var.env}-${var.app_name}")
  retention_in_days = "30"
}

resource "aws_secretsmanager_secret" "secrets" {
  count      = var.secrets_enable == true ? 1 : 0
  name       = "secrets/${var.env}/${var.app_name}"
  kms_key_id = var.kms_key_id
}

#Create task definitions for app services
resource "aws_ecs_task_definition" "ecs_task_definition" {
  family                   = "${var.project}-${var.app_name}_${var.env}"
  task_role_arn            = aws_iam_role.ecs_role.arn
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  requires_compatibilities = [var.launch_type == "omit" ? "FARGATE" : var.launch_type]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  container_definitions = jsonencode(
    concat(
      [
        // Static first container definition
        {
          name  = "${var.env}-${var.project}-${var.app_name}-task"
          image = var.ecr_repo_uri != "" ? "${var.ecr_repo_uri}:${var.ecr_image_tag}" : (var.is_create_repo ? "${module.ecr[0].repository_url}:${var.ecr_image_tag}" : "")
          #if no other containers are here all the cpu is for the service container
          cpu         = (var.additional_containers != [] && var.main_cpu != null) ? var.main_cpu : var.cpu
          memory      = (var.additional_containers != [] && var.main_memory != null) ? var.main_memory : var.memory
          environment = var.environment_variables
          essential   = true
          command     = var.command
          # ECS names this key startPeriod; passing var.healthcheck straight through sent
          # start_delay, which AWS drops silently, leaving every task with no start period.
          healthCheck = var.healthcheck == null ? null : {
            command     = var.healthcheck.command
            interval    = var.healthcheck.interval
            retries     = var.healthcheck.retries
            startPeriod = var.healthcheck.start_delay
            timeout     = var.healthcheck.timeout
          }
          readonlyRootFilesystem = var.readonly_root_filesystem
          secrets = var.secrets_enable ? concat(
            [
              {
                name      = "envs"
                valueFrom = aws_secretsmanager_secret.secrets[0].arn
              }
            ],
            [
              for secret_name, secret_arn in var.additional_secrets : {
                name      = secret_name
                valueFrom = secret_arn
              }
            ]
          ) : length(var.sensitive_environment_variables) > 0 ? var.sensitive_environment_variables : null
          portMappings = concat(
            [
              {
                containerPort = var.containerPort
                hostPort      = var.hostPort
              }
            ],
            var.additional_port_mappings // Concatenate additional port mappings
          )
          logConfiguration = {
            logDriver = "awslogs"
            options = {
              "awslogs-group"         = aws_cloudwatch_log_group.ecs_cw_log_group.name
              "awslogs-region"        = var.region
              "awslogs-stream-prefix" = "ecs"
            }
          }
        }
      ],
      // Dynamic additional containers
      [
        for container in var.additional_containers : {
          name                   = container.name
          image                  = container.image
          cpu                    = lookup(container, "cpu", null)
          memory                 = lookup(container, "memory", null)
          command                = lookup(container, "command", null)
          secrets                = lookup(container, "secrets", [])
          portMappings           = lookup(container, "portMappings", [])
          healthCheck            = lookup(container, "healthCheck", null)
          dependsOn              = lookup(container, "dependsOn", null)
          environment            = container.environment
          essential              = false
          readonlyRootFilesystem = var.readonly_root_filesystem
          logConfiguration = container.logConfiguration != null ? container.logConfiguration : {
            logDriver = "awslogs"
            options = {
              "awslogs-group"         = aws_cloudwatch_log_group.ecs_cw_log_group.name
              "awslogs-region"        = var.region
              "awslogs-stream-prefix" = "ecs"
            }
          }
        }
      ]
    )
  )
  dynamic "runtime_platform" {
    for_each = var.runtime_platform
    content {
      operating_system_family = runtime_platform.value.operating_system_family
      cpu_architecture        = runtime_platform.value.cpu_architecture
    }
  }
}



resource "aws_ecs_service" "service" {

  name                               = "${var.env}-${var.project}-${var.app_name}-service"
  cluster                            = var.cluster
  task_definition                    = aws_ecs_task_definition.ecs_task_definition.arn
  desired_count                      = var.desired_capacity
  launch_type                        = var.launch_type == "omit" ? null : var.launch_type
  enable_execute_command             = var.enable_execute_command
  force_new_deployment               = var.force_new_deployment
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.deployment_maximum_percent
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds

  tags = merge(
    {
      Env         = var.env
      Project     = var.project
      Application = var.app_name
    },
    can(var.network) && var.network != "" ? { Network = var.network } : {}
  )

  network_configuration {
    security_groups  = var.security_groups
    subnets          = var.subnets
    assign_public_ip = "false"
  }

  service_registries {
    registry_arn = aws_service_discovery_service.service.arn
    port         = var.containerPort
  }

  lifecycle {
    ignore_changes = [
      capacity_provider_strategy,
    ]
  }

  triggers = {
    redeployment = plantimestamp()
  }
}

################################################################################
# Cloudwatch log metric filters
################################################################################

resource "aws_cloudwatch_log_metric_filter" "ecs_cw_log_metric_filter" {
  count = length(var.log_metric_filters)

  name           = "${var.env}-${var.app_name}-log-metric-filter-${var.log_metric_filters[count.index].name}"
  pattern        = var.log_metric_filters[count.index].pattern
  log_group_name = aws_cloudwatch_log_group.ecs_cw_log_group.name

  metric_transformation {
    name      = "${var.env}-${var.app_name}-${var.log_metric_filters[count.index].name}"
    namespace = "LogMetrics"
    value     = "1"
  }
}
