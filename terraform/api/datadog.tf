module "datadog_integration_aws" {
  source = "git::git@github.com:vechain/terraform_infrastructure_modules.git//datadog?ref=v.1.0.23"
  project_name = "${local.env.project}"
  role_name  = "DatadogAWSIntegrationRole"
  aws_permissions_list = [
                "apigateway:GET",
                "autoscaling:Describe*",
                "cloudtrail:DescribeTrails",
                "cloudtrail:GetTrailStatus",
                "cloudtrail:LookupEvents",
                "cloudwatch:Describe*",
                "cloudwatch:Get*",
                "cloudwatch:List*",
                "ec2:Describe*",
                "ec2:GetTransitGatewayPrefixListReferences",
                "ec2:SearchTransitGatewayRoutes",
                "ecs:Describe*",
                "ecs:List*",
                "elasticloadbalancing:Describe*",
                "events:CreateEventBus",
                "health:DescribeEvents",
                "health:DescribeEventDetails",
                "health:DescribeAffectedEntities",
                "lambda:GetPolicy",
                "lambda:List*",
                "logs:DeleteSubscriptionFilter",
                "logs:DescribeLogGroups",
                "logs:DescribeLogStreams",
                "logs:DescribeSubscriptionFilters",
                "logs:FilterLogEvents",
                "logs:PutSubscriptionFilter",
                "logs:TestMetricFilter",
                "route53:List*",
                "s3:GetBucketLogging",
                "s3:GetBucketLocation",
                "s3:GetBucketNotification",
                "s3:GetBucketTagging",
                "s3:ListAllMyBuckets",
                "s3:PutBucketNotification",
                "sqs:ListQueues",
                "states:ListStateMachines",
                "states:DescribeStateMachine",
                "tag:GetResources",
                "tag:GetTagKeys",
                "tag:GetTagValues",
  ]
  
  filter_tags = []
  host_tags   = [" Env:${local.env.project}"]
  namespace_rules = {
    "us-east-1" = true  // Enable monitoring for us-east-1
    "eu-west-1" = true  // Enable monitoring for us-east-1
  }

  dashboard_title       = "${local.env.project} Dashboard"
  dashboard_description = "Monitoring dashboard for ${local.env.project}"
  layout_type          = "ordered"
  alert_id             = "some-alert-id"
  widget_type          = "timeseries"
  widget_title         = "Widget Title"
  widget_time_span     = "10m"
  secret_id            = local.env.datadog.secret_arn
}





