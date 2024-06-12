locals {
  configuration_name = substr("${local.env.environment}-${var.project}", 0, 28)
}
resource "aws_sns_topic" "chatbot_sns_topic" {
  name = "${local.env.environment}-CloudWatchAlarms"
  # Add other SNS topic configuration as needed
}

resource "awscc_chatbot_slack_channel_configuration" "slack_integration" {
  count              = local.env.environment == "prod" ? 1 : 0
  configuration_name = substr("${local.env.environment}-${var.project}", 0, 28)
  slack_channel_id   = local.env.slack_channel_id
  slack_workspace_id = local.env.slack_workspace_id
  iam_role_arn       = aws_iam_role.cloudwatch_read.arn
  guardrail_policies = ["arn:aws:iam::aws:policy/ReadOnlyAccess"]
  sns_topic_arns     = [aws_sns_topic.chatbot_sns_topic.arn]
}

# IAM Resources for CloudWatch Alarms
resource "aws_iam_role_policy_attachment" "attachment" {
  role       = aws_iam_role.cloudwatch_read.name
  policy_arn = aws_iam_policy.cloudwatch_policy.arn
}

resource "aws_iam_role" "cloudwatch_read" {
  name = "${local.configuration_name}-AwsChatBot-Slack-CloudWatch-ReadOnly"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "chatbot.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_policy" "cloudwatch_policy" {
  name        = "${local.configuration_name}-AwsChatBot-Slack-CloudWatch-ReadOnly"
  description = "IAM policy for CloudWatch access"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
              "logs:GetDataProtectionPolicy",
              "logs:GetDelivery",
              "logs:GetLogRecord",
              "logs:Describe*",
              "logs:List*",
              "logs:StartQuery",
              "logs:Unmask",
              "logs:GetDeliveryDestinationPolicy",
              "cloudwatch:List*",
              "logs:StopQuery",
              "logs:TestMetricFilter",
              "logs:GetDeliveryDestination",
              "logs:GetLogAnomalyDetector",
              "cloudwatch:Describe*",
              "logs:GetLogDelivery",
              "logs:GetDeliverySource",
              "logs:GetQueryResults",
              "logs:StartLiveTail",
              "logs:StopLiveTail",
              "logs:GetLogEvents",
              "logs:FilterLogEvents",
              "cloudwatch:Get*",
              "logs:GetLogGroupFields"
            ],
            "Effect": "Allow",
            "Resource": "*"
        }
    ]
}
EOF
}
