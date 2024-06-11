# Simple alarms for CloudWatch constructed from sub maps as local variables, merged into a simple_alarms_map
# NB each submap element must have a unique key or it may be overwritten in the merge
locals {

  waf_alarms = {for k, v in module.waf : "${k}_blk_rq" => {
    alarm_name          = "${local.env.environment}_WAF_blocked_requests_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 1
    threshold           = 1000
    namespace           = "AWS/WAFV2"
    period              = 180
    statistic           = "Maximum"
    metric_name         = "BlockedRequests"
    alarm_description   = "Waf Blocked Request max count"
    dimensions = {
      Region = local.env.region
      Rule   = "origin-rate-limiter"
      WebACL = split("/", split(":", v.waf_limiter_arn)[5])[2]
    }
    }
  }

  elb_response_alarms = { for k, v in module.ecs-lb-service-api : "${v.service_name}_slo_req" => {
    alarm_name          = "${v.service_name}_response_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 1000
    namespace           = "AWS/ApplicationELB"
    period              = 180
    statistic           = "Maximum"
    metric_name         = "TargetResponseTime"
    alarm_description   = "ELB Target slow responses"
    dimensions = {
      LoadBalancer = substr(split(":", v.alb_arn)[5], 13, -1)
    }
    }
  }

  elb_5xx_count_alarms = { for k, v in module.ecs-lb-service-api : "${v.service_name}_hi_5xx" => {
    alarm_name          = "${v.service_name}_5xx_count_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 1
    namespace           = "AWS/ApplicationELB"
    period              = 180
    statistic           = "Average"
    metric_name         = "HTTPCode_ELB_5XX_Count"
    alarm_description   = "ELB 5XX error count"
    dimensions = {
      LoadBalancer = substr(split(":", v.alb_arn)[5], 13, -1)
    }
    }
  }

  elb_connect_rate_alarms = { for k, v in module.ecs-lb-service-api : "${v.service_name}_hi_conr" => {
    alarm_name          = "${v.service_name}_connect_rate_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 100
    namespace           = "AWS/ApplicationELB"
    period              = 180
    statistic           = "Average"
    metric_name         = "NewConnectionCount"
    alarm_description   = "ELB connection rate"
    dimensions = {
      LoadBalancer = substr(split(":", v.alb_arn)[5], 13, -1)
    }
    }
  }

  elb_low_health_alarm = { for k, v in module.ecs-lb-service-api : "${v.service_name}_lo_h" => {
    alarm_name          = "${v.service_name}_low_health_alarm"
    comparison_operator = "LessThanThreshold"
    evaluation_periods  = 3
    threshold           = 1
    namespace           = "AWS/ApplicationELB"
    period              = 180
    statistic           = "Average"
    metric_name         = "HealthyHostCount"
    alarm_description   = "ELB TG no healthy hosts"
    dimensions = {
      LoadBalancer = substr(split(":", v.alb_arn)[5], 13, -1)
      TargetGroup  = split(":", v.alb_tg)[5]
    }
    }
  }

  elb_hi_unhealthy_alarm = { for k, v in module.ecs-lb-service-api : "${v.service_name}_hi_unh" => {
    alarm_name          = "${v.service_name}_hi_unhealthy_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 1
    namespace           = "AWS/ApplicationELB"
    period              = 180
    statistic           = "Average"
    metric_name         = "UnHealthyHostCount"
    alarm_description   = "ELB TG hi unhealthy hosts"
    dimensions = {
      LoadBalancer = substr(split(":", v.alb_arn)[5], 13, -1)
      TargetGroup  = split(":", v.alb_tg)[5]
    }
    }
  }

  ecs_backend_highcpu_alarm = {
    for k, v in module.ecs-backend-service : "${v.service_name}_hicpu" => {
      alarm_name          = "${v.service_name}_highcpu_alarm"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      evaluation_periods  = 3
      threshold           = 75
      namespace           = "AWS/ECS"
      period              = 300
      statistic           = "Average"
      metric_name         = "CPUUtilization"
      alarm_description   = "${v.service_name} high CPU load"
      dimensions = {
        ServiceName = v.service_name
        ClusterName = module.ecs-cluster.name
      }
    }
  }
  ecs_lb_highcpu_alarm = {
    for k, v in module.ecs-lb-service-api : "${v.service_name}_hicpu" => {
      alarm_name          = "${v.service_name}_highcpu_alarm"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      evaluation_periods  = 3
      threshold           = 75
      namespace           = "AWS/ECS"
      period              = 300
      statistic           = "Average"
      metric_name         = "CPUUtilization"
      alarm_description   = "${v.service_name} high CPU load"
      dimensions = {
        ServiceName = v.service_name
        ClusterName = module.ecs-cluster.name
      }
    }
  }

  ecs_highcpu_alarms = merge(local.ecs_backend_highcpu_alarm, local.ecs_lb_highcpu_alarm)

  ecs_backend_highmem_alarm = { 
    for k, v in module.ecs-backend-service : "${v.service_name}_himem" => {
    alarm_name          = "${v.service_name}_highmem_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 75
    namespace           = "AWS/ECS"
    period              = 300
    statistic           = "Average"
    metric_name         = "MemoryUtilization"
    alarm_description   = "${v.service_name} high memory consumption"
    dimensions = {
      ServiceName = v.service_name
      ClusterName = module.ecs-cluster.name
    }
   }
  }
  ecs_lb_highmem_alarm = { 
    for k, v in module.ecs-lb-service-api : "${v.service_name}_himem" => {
    alarm_name          = "${v.service_name}_highmem_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 75
    namespace           = "AWS/ECS"
    period              = 300
    statistic           = "Average"
    metric_name         = "MemoryUtilization"
    alarm_description   = "${v.service_name} high memory consumption"
    dimensions = {
      ServiceName = v.service_name
      ClusterName = module.ecs-cluster.name
    }
   }
  }

  ecs_highmem_alarms = merge(local.ecs_backend_highmem_alarm, local.ecs_lb_highmem_alarm)

  log_metric_filters_list = flatten([
    [
      for enabled_net_key, enabled_net in local.env_variables_data.enabled_nets : [
        for log_metric_api in enabled_net.api.log_metric_filters : {
          name      = log_metric_api.name
          pattern   = log_metric_api.pattern
          threshold = log_metric_api.threshold
        }
      ]
    ],
    [
      for enabled_net_key, enabled_net in local.env_variables_data.enabled_nets : [
        for log_metric_indexer in enabled_net.indexer.log_metric_filters : {
          name      = log_metric_indexer.name
          pattern   = log_metric_indexer.pattern
          threshold = log_metric_indexer.threshold
        }
      ]
    ]
  ])

  log_metric_alarm = {
    for k, v in log_metric_filters_list :
    "${v.name}_lma" => {
      alarm_name          = "${v.name}_logmetric_alarm"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      evaluation_periods  = 1
      threshold           = v.threshold
      namespace           = "LogMetrics"
      period              = 180
      stat                = "Sum"
      statistic           = "Sum"
      metric_name         = v.name
      alarm_description   = "${v.name} log metric"
    }
  }

  ec2_highcpu_alarm = { for k, v in aws_instance.mongodb_cluster : "${k}_ec2_hicpu" => {
    alarm_name          = "${k}_ec2_highcpu_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 3
    threshold           = 75
    namespace           = "AWS/EC2"
    period              = 300
    statistic           = "Average"
    metric_name         = "CPUUtilization"
    alarm_description   = "EC2 ${k} hi CPU"
    dimensions = {
      InstanceId = v.id
    }
    }
  }

  ec2_failure_alarm = { for k, v in aws_instance.mongodb_cluster : "${k}_ec2_fail" => {
    alarm_name          = "${k}_ec2_failure_alarm"
    comparison_operator = "GreaterThanOrEqualToThreshold"
    evaluation_periods  = 1
    threshold           = 1
    namespace           = "AWS/EC2"
    period              = 180
    statistic           = "Maximum"
    metric_name         = "StatusCheckFailed"
    alarm_description   = "EC2 ${k} StatusCheckFailed"
    dimensions = {
      InstanceId = v.id
    }
    }
  }

  simple_alarms_map = merge(
    local.waf_alarms,
    local.elb_response_alarms,
    local.elb_5xx_count_alarms,
    local.elb_connect_rate_alarms,
    local.elb_low_health_alarm,
    local.elb_hi_unhealthy_alarm,
    local.ecs_highcpu_alarms,
    local.ecs_highmem_alarms,
    local.log_metric_alarm,
    local.ec2_highcpu_alarm,
    local.ec2_failure_alarm
  )
}

# Expression alarms for CloudWatch constructed from sub maps as local variables, merged into a expressions_alarms_map
# NB each submap element must have a unique key or it may be overwritten in the merge
locals {
  expression_alarms_map = merge()
}

module "cloud_watch_alarms" {
  source                   = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudwatchalarm?ref=v.1.0.2"
  sns_topic_enabled        = true
  topic_name               = "${local.env.environment}-CloudWatchAlarms"
  email_subscriptions      = []
  create_slack_integration = true
  configuration_name       = substr("${local.env.environment}-${var.project}", 0, 28)

  slack_channel_id   = local.env.slack_alert_channel
  slack_workspace_id = local.env.slack_alert_workspace

  simple_alarms     = local.simple_alarms_map
  expression_alarms = local.expression_alarms_map
}
