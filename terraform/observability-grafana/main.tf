data "aws_region" "current" {}

resource "terraform_data" "workspace_guard" {
  lifecycle {
    precondition {
      condition     = contains(["prod"], terraform.workspace)
      error_message = "Use workspace 'prod' only (not default). Example: terraform workspace select -or-create prod"
    }
  }
}

# AMP datasource — SigV4-signed queries against the workspace created
# by terraform/observability/. The AMG workspace role already has
# AmazonPrometheusQueryAccess plus the alerting scope (see main.tf in
# that stack), so no extra IAM here.
resource "grafana_data_source" "amp" {
  type = "prometheus"
  name = "AMP"
  uid  = "amp"
  url  = data.terraform_remote_state.observability.outputs.amp_endpoint

  json_data_encoded = jsonencode({
    httpMethod      = "POST"
    sigV4Auth       = true
    sigV4AuthType   = "default"
    sigV4Region     = data.aws_region.current.name
    prometheusType  = "Prometheus"
    manageAlerts    = true
    alertmanagerUid = "amp-alertmanager"
  })
}

# CloudWatch datasource — for log-based diagnostics via Logs Insights
# and any metrics that stay in CW (ECS task-level CPU/mem, ALB, etc.).
# AMG workspace role has CloudWatchReadOnlyAccess.
resource "grafana_data_source" "cloudwatch" {
  type = "cloudwatch"
  name = "CloudWatch"
  uid  = "cloudwatch"

  json_data_encoded = jsonencode({
    authType      = "default"
    defaultRegion = data.aws_region.current.name
  })
}
