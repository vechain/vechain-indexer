data "aws_region" "current" {}

resource "terraform_data" "workspace_guard" {
  lifecycle {
    precondition {
      condition     = contains(["prod"], terraform.workspace)
      error_message = "Use workspace 'prod' only (not default). Example: terraform workspace select -or-create prod"
    }
  }
}

# IAM for both datasources comes from the AMG workspace role in
# terraform/observability/.
resource "grafana_data_source" "amp" {
  type = "prometheus"
  name = "AMP"
  uid  = "amp"
  url  = data.terraform_remote_state.observability.outputs.amp_endpoint

  json_data_encoded = jsonencode({
    httpMethod     = "POST"
    sigV4Auth      = true
    sigV4AuthType  = "workspace-iam-role"
    sigV4Region    = data.aws_region.current.name
    prometheusType = "Prometheus"
  })
}

resource "grafana_data_source" "cloudwatch" {
  type = "cloudwatch"
  name = "CloudWatch"
  uid  = "cloudwatch"

  json_data_encoded = jsonencode({
    authType      = "default"
    defaultRegion = data.aws_region.current.name
  })
}
