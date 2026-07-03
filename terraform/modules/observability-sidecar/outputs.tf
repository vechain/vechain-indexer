output "container_definition" {
  description = "Entry to append to the ECS task's additional_containers list."
  value = {
    name    = "adot-metrics"
    image   = "public.ecr.aws/aws-observability/aws-otel-collector:${var.adot_image_tag}"
    memory  = var.memory_limit_mib
    command = ["--config=env:CONFIG_CONTENT"]
    environment = [
      {
        name  = "CONFIG_CONTENT"
        value = local.rendered_otel_config
      },
    ]
    secrets      = []
    portMappings = []
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = var.log_group_name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "adot-sidecar"
      }
    }
    # ADOT image is FROM scratch — CMD-SHELL exits before running; use
    # the shipped `/healthcheck` binary instead.
    healthCheck = {
      command     = ["CMD", "/healthcheck"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 30
    }
  }
}

output "amp_remote_write_statement" {
  description = "Entry to append to a module's `extra_statements` list (aws_iam_policy_document statement shape)."
  value = {
    sid       = "AMPRemoteWrite${title(var.service_name)}"
    effect    = "Allow"
    actions   = ["aps:RemoteWrite"]
    resources = [var.amp_workspace_arn]
  }
}

output "amp_remote_write_policy_json" {
  description = "Ready-to-use JSON policy for aws_iam_role_policy consumers that attach the statement directly."
  value = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["aps:RemoteWrite"]
      Resource = var.amp_workspace_arn
    }]
  })
}
