# WAF blocked-request alarm. The rest of the previous CloudWatch alarm
# set was either duplicated by the AMP alerts landed in P7 or fired on
# routine blue/green teardown; only this one carries a unique signal
# worth paging on.
locals {
  waf_alarms = { for k, v in module.waf : "${k}_blk_rq" => {
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

  simple_alarms_map     = local.waf_alarms
  expression_alarms_map = {}
}

module "cloud_watch_alarms" {
  count                    = local.env.environment == "dev" ? 0 : 1
  source                   = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudwatchalarm?ref=v.1.0.2"
  sns_topic_enabled        = false
  topic_name               = data.terraform_remote_state.vpc.outputs.chatbot_sns_topic_name
  email_subscriptions      = []
  create_slack_integration = false

  simple_alarms     = local.simple_alarms_map
  expression_alarms = local.expression_alarms_map
}
