locals {
  dashboards = {
    overview = "${path.module}/dashboards/overview.json"
    logs     = "${path.module}/dashboards/logs.json"
    edge     = "${path.module}/dashboards/edge.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
