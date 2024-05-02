module "datadog_integration_aws" {
  source = "git::git@github.com:vechain/terraform_infrastructure_modules.git//datadog?ref=223-module-for-datadog"
  account_id = local.env.datadog.account_id
  account_name = local.env.datadog.account_name
  role_name  = "CustomDatadogAWSIntegrationRole"
  aws_permissions_list = [
    "logs:PutLogEvents",
    "logs:DescribeLogStreams",
    "logs:CreateLogStream",

  // Application Load Balancer (ELB) permissions
  "elasticloadbalancing:Describe*",

  // AWS Billing permissions (Note: Billing permissions are sensitive and should be tightly controlled)
  "aws-portal:ViewBilling",
  "aws-portal:ViewUsage",

  // CloudFront permissions
  "cloudfront:Get*",
  "cloudfront:List*",

  // ElastiCache permissions
  "elasticache:Describe*",

  // ECS permissions
  "ecs:Describe*",

  // RDS permissions
  "rds:Describe*",
  ]
  
  filter_tags = []
  host_tags   = ["Env:${local.env.environment}", "datadog:enabled"]
  namespace_rules = {
    auto_scaling = false
    opsworks     = false
  }
  excluded_regions = ["us-west-1", "us-west-2"]

  dashboard_title       = "Your Dashboard Title"
  dashboard_description = "A brief description of the dashboard"
  layout_type          = "ordered"
  is_read_only         = false
  alert_id             = "some-alert-id"
  widget_type          = "timeseries"
  widget_title         = "Widget Title"
  widget_time_span     = "10m"
  secret_id            = local.env.datadog.app_id
}



