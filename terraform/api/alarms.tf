# AMP rules threshold metrics the tasks report about themselves, so those series go absent
# rather than breach when a task dies. These read ECS and ALB metrics instead.
locals {
  alarms_enabled    = local.observability_sidecar_enabled
  alerts_topic_arns = local.alarms_enabled ? [data.terraform_remote_state.observability.outputs.alerts_topic_arn] : []
  alarm_name_prefix = "${local.env.environment}-${var.project}"

  # Alarm descriptions are load-bearing: sns_to_slack.py splits them on the first " — ".
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

# 10m window: the indexers deploy at deployment_minimum_healthy_percent = 0, so a rolling
# replacement legitimately sits at zero running tasks for minutes.
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

# No HealthyHostCount companion: an empty target group (the dead colour) is indistinguishable
# from a dead one on that metric.
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

# Not 0: idle_timeout is the module default of 60s, so a trickle of 504s is expected.
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

# 300/5m is the 1 req/s of the HighApi5xxRate AMP rule; keep the two in step.
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

# 10s matches the app's own timing.very-slow-threshold-ms.
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

# RDS

locals {
  alarm_pg = local.alarms_enabled ? { for net in keys(local.pg_nets) : net => aws_db_instance.postgres[net].identifier } : {}

  alarm_pg_headers = {
    for net in keys(local.env.enabled_nets) :
    net => "[${local.observability_env}/${local.observability_deployment}/${local.network_label[net]}] postgres"
  }
}

# 10% of the initial size: the volume autoscales, so this fires only when growth has outrun it.
resource "aws_cloudwatch_metric_alarm" "pg_free_storage" {
  for_each = local.alarm_pg

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-pg-free-storage"
  alarm_description = "${local.alarm_pg_headers[each.key]}: Free storage low — under 10% of the initial volume; check max_allocated_storage."

  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = each.value }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "LessThanThreshold"
  threshold           = local.pg_nets[each.key].allocated_storage_gb * 0.1 * 1073741824
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

resource "aws_cloudwatch_metric_alarm" "pg_cpu" {
  for_each = local.alarm_pg

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-pg-cpu"
  alarm_description = "${local.alarm_pg_headers[each.key]}: CPU above 85% for 15 minutes — a backfill or a query plan has gone wrong."

  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = each.value }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  comparison_operator = "GreaterThanThreshold"
  threshold           = 85
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

resource "aws_cloudwatch_metric_alarm" "pg_freeable_memory" {
  for_each = local.alarm_pg

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-pg-memory"
  alarm_description = "${local.alarm_pg_headers[each.key]}: Freeable memory under 1 GiB — the working set no longer fits; expect I/O to climb."

  namespace           = "AWS/RDS"
  metric_name         = "FreeableMemory"
  dimensions          = { DBInstanceIdentifier = each.value }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 3
  comparison_operator = "LessThanThreshold"
  threshold           = 1073741824
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}

# Both services' Hikari pools together open well under 100 connections; more means a leak.
resource "aws_cloudwatch_metric_alarm" "pg_connections" {
  for_each = local.alarm_pg

  alarm_name        = "${local.alarm_name_prefix}-${each.key}-pg-connections"
  alarm_description = "${local.alarm_pg_headers[each.key]}: Too many connections — above 100, more than the indexer and API pools can open."

  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBInstanceIdentifier = each.value }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "GreaterThanThreshold"
  threshold           = 100
  treat_missing_data  = "notBreaching"

  alarm_actions = local.alerts_topic_arns
  ok_actions    = local.alerts_topic_arns
}
