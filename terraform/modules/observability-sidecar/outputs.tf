output "container_definition" {
  description = "Entry to append to the ECS task's `additional_containers` list. Sidecar is essential=false so a crash does not restart the app task."
  # Shape is aligned to the vechain/terraform_infrastructure_modules
  # ecs-backend-service `additional_containers` object type
  # (name/image/cpu/memory/environment/command/secrets/portMappings/
  # healthCheck/dependsOn only). The upstream schema does not declare
  # logConfiguration on additional_containers, so ADOT's own operational
  # logs (warn-level stderr) do not go to CloudWatch today. Sidecar
  # self-metrics still reach AMP because ADOT exports its own
  # `otelcol_*` counters via the same remote_write path. Follow-up:
  # extend the upstream module to accept logConfiguration on additional
  # containers, then plumb it through here.
  value = {
    name    = "adot-metrics"
    image   = "public.ecr.aws/aws-observability/aws-otel-collector:${var.adot_image_tag}"
    memory  = var.memory_reservation
    command = ["--config=env:CONFIG_CONTENT"]
    environment = [
      {
        name  = "CONFIG_CONTENT"
        value = local.rendered_otel_config
      },
    ]
    secrets      = []
    portMappings = []
    # ADOT image is FROM scratch (no shell), so CMD-SHELL healthchecks
    # exit before running. AWS ships a `/healthcheck` binary that probes
    # the health_check extension on :13133 internally.
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
