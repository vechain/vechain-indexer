locals {
  dashboards = {
    veworld-observability = "${path.module}/dashboards/veworld-observability.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
