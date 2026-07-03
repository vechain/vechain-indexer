locals {
  name_prefix = "${var.project}-${terraform.workspace}"

  # Ratio thresholds used across alert rules.
  saturation_threshold = 0.8

  # Ported from agent-marketplace's observability-aws stack. Starter set is
  # limited to signals whose source metrics we actually have — API 5xx rate
  # from Spring's http_server_requests_seconds_count, and per-task cgroup
  # CPU/memory from the awsecscontainermetrics receiver. AWS-native signals
  # that agent-marketplace pulls via YACE (ALB 5xx, ECS task drift, RDS,
  # ElastiCache) are out of scope until we add a CloudWatch exporter.
  #
  # `env`/`deployment`/`network`/`service` come through as series labels
  # from the sidecar's external_labels. Alerts intentionally do not stamp
  # them into `labels:` — the alertmanager template reads them from the
  # underlying series via .CommonLabels / .Alerts[].Labels.
  alert_rules_yaml = <<-YAML
    groups:
      - name: ${local.name_prefix}-alerts
        rules:
          - alert: HighApi5xxRate
            expr: sum by (env, deployment, network, service) (rate(http_server_requests_seconds_count{service="api", outcome="SERVER_ERROR"}[5m])) > 1
            for: 5m
            labels:
              severity: warning
            annotations:
              title: "High API 5xx rate"
              summary: "API 5xx response rate is above 1 req/s for over 5 minutes."

          - alert: EcsTaskCpuHigh
            expr: (ecs_task_cpu_utilized_None / ecs_task_cpu_reserved_None) > ${local.saturation_threshold}
            for: 10m
            labels:
              severity: warning
            annotations:
              title: "High CPU usage"
              summary: "ECS task CPU is above ${format("%.0f", local.saturation_threshold * 100)}% for over 10 minutes."

          - alert: EcsTaskMemoryHigh
            expr: (ecs_task_memory_utilized_Megabytes / ecs_task_memory_reserved_Megabytes) > ${local.saturation_threshold}
            for: 10m
            labels:
              severity: warning
            annotations:
              title: "High memory usage"
              summary: "ECS task memory is above ${format("%.0f", local.saturation_threshold * 100)}% for over 10 minutes."
  YAML

  # AMP Alertmanager `sns_configs` defaults to upstream Alertmanager's
  # plain-text body. Override `sns.default.message` so the bridge Lambda
  # forwards the final Slack post verbatim. group_by includes deployment
  # / network / service so blue-only or one-service issues don't merge
  # with green-only or other-service ones.
  alertmanager_definition = <<-YAML
    template_files:
      default.tmpl: |
        {{ define "sns.default.message" -}}
        *[{{ .CommonLabels.env }}/{{ .CommonLabels.deployment }}/{{ .CommonLabels.network }}] {{ .CommonLabels.service }}: {{ if .CommonAnnotations.title }}{{ .CommonAnnotations.title }}{{ else }}{{ .CommonLabels.alertname }}{{ end }}*{{ if eq .Status "resolved" }} — resolved{{ else }}{{ if or (gt (len .Alerts.Firing) 1) (gt (len .Alerts.Resolved) 0) }} — {{ len .Alerts.Firing }} firing{{ if gt (len .Alerts.Resolved) 0 }}, {{ len .Alerts.Resolved }} recovered{{ end }}{{ end }}
        {{ .CommonAnnotations.summary }}{{ with .Alerts.Firing }}{{ if (index . 0).Labels.task_id }}
        Tasks: {{ range $i, $a := . }}{{ if $i }}, {{ end }}{{ printf "%.8s" $a.Labels.task_id }}{{ end }}{{ end }}{{ end }}{{ end }}
        {{- end }}
    alertmanager_config: |
      templates:
        - default.tmpl
      route:
        receiver: 'slack-sns'
        group_by: ['alertname', 'env', 'deployment', 'network', 'service']
        group_wait: 30s
        group_interval: 5m
        repeat_interval: 4h
      receivers:
        - name: 'slack-sns'
          sns_configs:
            - topic_arn: '${aws_sns_topic.alerts.arn}'
              sigv4:
                region: '${data.aws_region.current.name}'
  YAML
}
