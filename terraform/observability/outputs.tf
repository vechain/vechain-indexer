output "amp_workspace_id" {
  description = "AMP workspace ID."
  value       = aws_prometheus_workspace.this.id
}

output "amp_workspace_arn" {
  description = "AMP workspace ARN. Used by sidecar tasks as the Resource on aps:RemoteWrite."
  value       = aws_prometheus_workspace.this.arn
}

output "amp_endpoint" {
  description = "AMP workspace endpoint (base URL, trailing slash preserved). Sidecar remote-writes to <endpoint>api/v1/remote_write with SigV4 signing."
  value       = aws_prometheus_workspace.this.prometheus_endpoint
}

output "amg_workspace_id" {
  description = "AMG workspace ID."
  value       = aws_grafana_workspace.this.id
}

output "amg_workspace_endpoint" {
  description = "AMG workspace endpoint. Used by the grafana provider in the dashboards stack."
  value       = aws_grafana_workspace.this.endpoint
}

output "amg_sa_token_secret_arn" {
  description = "Secrets Manager ARN holding the Grafana admin SA token. Downstream stacks read the value through a secretsmanager data source at apply time."
  value       = aws_secretsmanager_secret.amg_sa_token.arn
}
