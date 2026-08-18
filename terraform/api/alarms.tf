# CloudWatch alarms for task failures, published to the SNS topic the AMP Alertmanager
# already uses. Deliberately a second source: every AMP rule is a threshold over a metric the
# task reports about itself, so those series vanish rather than breach when a task dies. The
# metrics below are published by ECS and the ALB, so they keep reporting when the task doesn't.
#
# Gated on the same flag as the sidecars — both need the observability stack applied for this
# environment, and that stack owns the topic and the policy that lets CloudWatch publish to it.
locals {
  alarms_enabled    = local.observability_sidecar_enabled
  alerts_topic_arns = local.alarms_enabled ? [data.terraform_remote_state.observability.outputs.alerts_topic_arn] : []
  alarm_name_prefix = "${local.env.environment}-${var.project}"

  # The Alertmanager template builds "[env/deployment/network] service: Title" out of
  # .CommonLabels. CloudWatch payloads carry no labels, so the same prefix is baked into the
  # alarm description and sns_to_slack.py splits it off the summary on the first " — ".
  alarm_headers = {
    for pair in setproduct(keys(local.env.enabled_nets), ["api", "indexer"]) :
    "${pair[0]}-${pair[1]}" => "[${local.observability_env}/${local.observability_deployment}/${local.network_label[pair[0]]}] ${pair[1]}"
  }

  alarm_ecs_services = local.alarms_enabled ? merge(
    { for net in keys(local.env.enabled_nets) : "${net}-api" => module.ecs-lb-service-api[net].service_name },
    { for net in keys(local.env.enabled_nets) : "${net}-indexer" => module.ecs-backend-service[net].service_name },
  ) : {}

  alarm_albs = local.alarms_enabled ? {
    for net in keys(local.env.enabled_nets) : net => {
      load_balancer = module.ecs-lb-service-api[net].alb_arn_suffix
      target_group  = module.ecs-lb-service-api[net].alb_tg_arn_suffix
    }
  } : {}
}

# Ten consecutive breaching minutes, not two: the indexer services deploy at
# deployment_minimum_healthy_percent = 0, so a rolling replacement legitimately sits at zero
# running tasks for several minutes. The ALB alarms below are the fast path for the API.
#
# notBreaching because the expression only goes absent when Container Insights stops publishing
# for the service, which means the service is gone — a terraform destroy, not an incident. A
# service scaled to zero (the dead colour) still publishes, at desired = running = 0.
resource "aws_cloudwatch_metric_alarm" "ecs_tasks_below_desired" {
  for_each = local.alarm_ecs_services

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-tasks-below-desired"
  alarm_description = "${local.alarm_headers[each.key]}: Tasks below desired count — ECS is running fewer tasks than the service asks for."

  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  evaluation_periods  = 10
  datapoints_to_alarm = 10
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "shortfall"
    expression  = "desired - running"
    label       = "Tasks below desired count"
    return_data = true
  }

  metric_query {
    id = "desired"
    metric {
      namespace   = "ECS/ContainerInsights"
      metric_name = "DesiredTaskCount"
      dimensions  = { ClusterName = module.ecs-cluster.name, ServiceName = each.value }
      period      = 60
      stat        = "Maximum"
    }
  }

  metric_query {
    id = "running"
    metric {
      namespace   = "ECS/ContainerInsights"
      metric_name = "RunningTaskCount"
      dimensions  = { ClusterName = module.ecs-cluster.name, ServiceName = each.value }
      period      = 60
      stat        = "Minimum"
    }
  }

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

# No HealthyHostCount-below-floor companion: the dead colour is scaled to zero out of band by
# set_dead_prod_service_state.sh, and an empty target group is indistinguishable from a dead one
# on that metric. UnHealthyHostCount only rises above zero while targets are registered and
# failing; losing them all is covered by tasks-below-desired and the ELB 5xx alarm.
resource "aws_cloudwatch_metric_alarm" "alb_unhealthy_hosts" {
  for_each = local.alarm_albs

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-api-alb-unhealthy-hosts"
  alarm_description = "${local.alarm_headers["${each.key}-api"]}: Unhealthy targets — API tasks are registered but failing ALB health checks."

  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  dimensions          = { LoadBalancer = each.value.load_balancer, TargetGroup = each.value.target_group }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

# 25 per 5m, not 0: idle_timeout on these ALBs is the module default of 60s, so a trickle of
# 504s is expected on the slower query endpoints. Recalibrate off HTTPCode_ELB_5XX_Count once
# there is a baseline, or raise idle_timeout.
resource "aws_cloudwatch_metric_alarm" "alb_elb_5xx" {
  for_each = local.alarm_albs

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-api-alb-5xx"
  alarm_description = "${local.alarm_headers["${each.key}-api"]}: ALB returning 5xx — 502/503 with no healthy target, or 504 from the idle timeout."

  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_ELB_5XX_Count"
  dimensions          = { LoadBalancer = each.value.load_balancer }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 25
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

# 300 per 5m is the 1 req/s of the HighApi5xxRate AMP rule, measured at the load balancer so it
# survives the task dying. Keep the two thresholds in step.
resource "aws_cloudwatch_metric_alarm" "alb_target_5xx" {
  for_each = local.alarm_albs

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-api-alb-target-5xx"
  alarm_description = "${local.alarm_headers["${each.key}-api"]}: API returning 5xx — above 1 req/s, measured at the load balancer."

  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  dimensions          = { LoadBalancer = each.value.load_balancer, TargetGroup = each.value.target_group }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 300
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

# 10s matches the app's own timing.very-slow-threshold-ms, so a p95 there means most requests
# are past the point the service already considers pathological.
resource "aws_cloudwatch_metric_alarm" "alb_target_latency" {
  for_each = local.alarm_albs

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-api-alb-latency"
  alarm_description = "${local.alarm_headers["${each.key}-api"]}: Slow responses — p95 above 10s at the load balancer."

  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  dimensions          = { LoadBalancer = each.value.load_balancer, TargetGroup = each.value.target_group }
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "GreaterThanThreshold"
  threshold           = 10
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}
