locals {
  dashboards = {
    overview = "${path.module}/dashboards/overview.json"
    logs     = "${path.module}/dashboards/logs.json"
    backups  = "${path.module}/dashboards/backups.json"
  }
}

resource "grafana_dashboard" "this" {
  for_each = local.dashboards

  config_json = file(each.value)
}
