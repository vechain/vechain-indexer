locals {
  dashboards = {
    vechain-observability = "${path.module}/dashboards/vechain-observability.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
