locals {
  dashboards = {
    overview = "${path.module}/dashboards/overview.json"
    logs     = "${path.module}/dashboards/logs.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
