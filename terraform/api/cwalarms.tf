# Empty since the regional WAF went: see PR history for the retired alarm set.
locals {
  simple_alarms_map     = {}
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
