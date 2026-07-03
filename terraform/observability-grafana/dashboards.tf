locals {
  dashboards = {
    overview = "${path.module}/dashboards/overview.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
