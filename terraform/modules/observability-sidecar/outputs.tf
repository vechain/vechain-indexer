output "container_definition" {
  # Shape matches the upstream ecs-backend-service `additional_containers`
  # object type — no logConfiguration field, so ADOT stderr does not
  # reach CloudWatch. Sidecar self-metrics still flow via remote_write.
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
  description = "Entry to append to the ECS task role's `extra_statements` list. Grants aps:RemoteWrite on the AMP workspace."
  value = {
    sid       = "AMPRemoteWrite${title(var.service_name)}"
    effect    = "Allow"
    actions   = ["aps:RemoteWrite"]
    resources = [var.amp_workspace_arn]
  }
}
