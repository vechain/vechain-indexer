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

  # sigV4AuthType = "default" (SDK credential chain, resolves to the
  # workspace's role_arn) — required because the parent AMG workspace is
  # permission_type = "CUSTOMER_MANAGED". "workspace-iam-role" is rejected
  # by the workspace with "non-allowed auth method" and is only valid for
  # SERVICE_MANAGED workspaces.
  #
  # manageAlerts surfaces the AMP rules + their state under Alerting →
  # Alert rules; alertmanagerUid links them to the AMP Alertmanager DS
  # below so the silence UI works.
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

resource "grafana_data_source" "amp_alertmanager" {
  type = "alertmanager"
  name = "AMP Alertmanager"
  uid  = "amp-alertmanager"
  # AMP exposes Alertmanager at `<prometheus_endpoint>/alertmanager`.
  url = "${trimsuffix(data.terraform_remote_state.observability.outputs.amp_endpoint, "/")}/alertmanager"

  json_data_encoded = jsonencode({
    implementation             = "prometheus"
    sigV4Auth                  = true
    sigV4AuthType              = "default"
    sigV4Region                = data.aws_region.current.name
    handleGrafanaManagedAlerts = false
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
