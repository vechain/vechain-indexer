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
