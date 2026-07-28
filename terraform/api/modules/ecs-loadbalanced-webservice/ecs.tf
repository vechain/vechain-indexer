data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

data "aws_region" "current" {}

data "aws_elb_service_account" "default" {}


resource "aws_cloudwatch_log_group" "ecs_cw_log_group" {
  name              = lower("${var.project}-${var.env}-${var.app_name}")
  retention_in_days = "30"
}

resource "aws_secretsmanager_secret" "secrets" {
  count      = var.secrets_enable ? 1 : 0
  name       = "secrets/${var.env}/${var.app_name}"
  kms_key_id = var.kms
}

#Create task definitions for app services
resource "aws_ecs_task_definition" "ecs_task_definition" {
  family                   = "${var.env}-${var.project}-${var.app_name}"
  task_role_arn            = aws_iam_role.ecs_role.arn
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  requires_compatibilities = [var.launch_type == "omit" ? "FARGATE" : var.launch_type]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  container_definitions = jsonencode(
    concat(
      [
        # Static first container definition
        {
          name  = "${var.env}-${var.project}-${var.app_name}-task"
          image = var.ecr_repo_uri != "" ? "${var.ecr_repo_uri}:${var.ecr_image_tag}" : (var.is_create_repo ? "${aws_ecr_repository.repo[0].repository_url}:${var.ecr_image_tag}" : "")
          #if no other containers are here all the cpu is for the service container
          cpu                    = (var.additional_containers != [] && var.main_cpu != null) ? var.main_cpu : var.cpu
          memory                 = (var.additional_containers != [] && var.main_memory != null) ? var.main_memory : var.memory
          environment            = var.environment_variables
          essential              = true
          healthCheck            = var.healthcheck
          readonlyRootFilesystem = var.readonly_root_filesystem
          # Sensitive environment variables / secrets
          secrets = var.secrets_enable == true ? concat(
            [
              {
                name      = "envs",
                valueFrom = aws_secretsmanager_secret.secrets[0].arn
              }
            ],
          ) : length(var.sensitive_environment_variables) > 0 ? var.sensitive_environment_variables : null

          portMappings = concat(
            [
              {
                containerPort = var.https_tg_port
                hostPort      = var.https_tg_port
              }
            ],
            var.additional_port_mappings // Concatenate additional port mappings
          )
          logConfiguration = {
            logDriver = "awslogs"
            options = {
              "awslogs-group"         = aws_cloudwatch_log_group.ecs_cw_log_group.name
              "awslogs-region"        = "eu-west-1"
              "awslogs-stream-prefix" = "ecs"
            }
          }
        }
      ],
      # Dynamic additional containers
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
          essential              = false # Mark non-primary containers as non-essential if needed 
          readonlyRootFilesystem = var.readonly_root_filesystem
          logConfiguration = container.logConfiguration != null ? container.logConfiguration : {
            logDriver = "awslogs"
            options = {
              "awslogs-group"         = aws_cloudwatch_log_group.ecs_cw_log_group.name
              "awslogs-region"        = data.aws_region.current.name
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


resource "aws_ecs_service" "service_alb" {

  name                              = "${var.env}-${var.project}-${var.app_name}-service"
  cluster                           = var.cluster_name
  task_definition                   = aws_ecs_task_definition.ecs_task_definition.arn
  desired_count                     = var.desired_count
  health_check_grace_period_seconds = var.health_check_grace_period_seconds
  #iam_role        = aws_iam_role.ecs_task_execution_role.arn
  launch_type                        = var.launch_type == "omit" ? null : var.launch_type
  enable_execute_command             = var.enable_execute_command
  force_new_deployment               = var.force_new_deployment
  deployment_maximum_percent         = var.deployment_maximum_percent
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  tags = merge(
    {
      Env         = var.env
      Project     = var.project
      Application = var.app_name
    },
    can(var.network) && var.network != "" ? { Network = var.network } : {}
  )
  #ordered_placement_strategy {
  #  type  = "binpack"
  #  field = "cpu"
  #}

  load_balancer {
    target_group_arn = var.load_balancer_type == "application" ? aws_alb_target_group.alb_target_group_https[0].arn : aws_alb_target_group.nlb_target_group_tcp[0].arn
    container_name   = "${var.env}-${var.project}-${var.app_name}-task"
    container_port   = var.container_port
  }

  network_configuration {
    security_groups  = var.ecs_sg
    subnets          = var.app_subnets
    assign_public_ip = var.assign_public_ip
  }

  service_registries {
    registry_arn = aws_service_discovery_service.service.arn
    port         = var.container_port
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
