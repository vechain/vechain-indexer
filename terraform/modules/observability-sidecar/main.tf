# Renders the ADOT collector config into a template string. The parent
# stack passes this to the ECS task via the CONFIG_CONTENT env var, which
# ADOT reads with `--config=env:CONFIG_CONTENT`. This avoids needing to
# ship the config file inside the container image.
locals {
  rendered_otel_config = templatefile("${path.module}/otel-config.yaml.tftpl", {
    aws_region   = var.aws_region
    amp_endpoint = var.amp_endpoint
    app_port     = var.app_port
    service_name = var.service_name
    env          = var.env
    deployment   = var.deployment
    network      = var.network
  })
}
