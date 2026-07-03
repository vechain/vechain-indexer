locals {
  dashboards = {
    indexer-sync = "${path.module}/dashboards/indexer-sync.json"
    api-red      = "${path.module}/dashboards/api-red.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
