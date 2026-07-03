# P7 — AMP recording + alerting rules with Slack delivery, ported from
# agent-marketplace's observability-aws stack. Pipeline:
#   AMP Alertmanager → SNS → sns_to_slack Lambda → Slack webhook
#
# Slack webhook value comes in via TF_VAR_slack_webhook_url. While unset,
# the secret holds the `placeholder` sentinel and the Lambda no-ops
# rather than POSTing to an empty URL — so the plumbing can apply before
# the webhook exists.

resource "aws_secretsmanager_secret" "slack_webhook" {
  name                    = "${local.name_prefix}-slack-webhook"
  description             = "Slack incoming-webhook URL for AMP alert delivery. Value from TF_VAR_slack_webhook_url."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "slack_webhook" {
  secret_id     = aws_secretsmanager_secret.slack_webhook.id
  secret_string = var.slack_webhook_url == "" ? "placeholder" : var.slack_webhook_url
}

resource "aws_sns_topic" "alerts" {
  name = "${local.name_prefix}-alerts"
}

# SourceArn + SourceAccount are confused-deputy hardening — without them any
# AMP workspace in any account targeting this topic ARN could publish.
data "aws_iam_policy_document" "alerts_topic" {
  statement {
    sid    = "AllowAMPPublish"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["aps.amazonaws.com"]
    }
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alerts.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_prometheus_workspace.this.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.alerts_topic.json
}

resource "aws_cloudwatch_log_group" "sns_to_slack" {
  name              = "/aws/lambda/${local.name_prefix}-sns-to-slack"
  retention_in_days = local.env.log_retention_days
}

data "aws_iam_policy_document" "sns_to_slack_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "sns_to_slack" {
  name               = "${local.name_prefix}-sns-to-slack"
  assume_role_policy = data.aws_iam_policy_document.sns_to_slack_assume.json
}

resource "aws_iam_role_policy_attachment" "sns_to_slack_basic" {
  role       = aws_iam_role.sns_to_slack.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "sns_to_slack_inline" {
  statement {
    sid       = "ReadSlackWebhook"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.slack_webhook.arn]
  }
}

resource "aws_iam_role_policy" "sns_to_slack" {
  name   = "${local.name_prefix}-sns-to-slack"
  role   = aws_iam_role.sns_to_slack.id
  policy = data.aws_iam_policy_document.sns_to_slack_inline.json
}

data "archive_file" "sns_to_slack" {
  type        = "zip"
  source_file = "${path.module}/lambda/sns_to_slack.py"
  output_path = "${path.module}/.terraform/sns_to_slack.zip"
}

resource "aws_lambda_function" "sns_to_slack" {
  function_name    = "${local.name_prefix}-sns-to-slack"
  role             = aws_iam_role.sns_to_slack.arn
  filename         = data.archive_file.sns_to_slack.output_path
  source_code_hash = data.archive_file.sns_to_slack.output_base64sha256
  runtime          = "python3.12"
  handler          = "sns_to_slack.handler"
  timeout          = 10
  memory_size      = 128

  environment {
    variables = {
      SLACK_WEBHOOK_SECRET_ARN = aws_secretsmanager_secret.slack_webhook.arn
    }
  }

  depends_on = [aws_cloudwatch_log_group.sns_to_slack]
}

resource "aws_lambda_permission" "sns_to_slack_invoke" {
  statement_id  = "AllowSNSInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.sns_to_slack.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.alerts.arn
}

resource "aws_sns_topic_subscription" "sns_to_slack" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.sns_to_slack.arn

  # Without this the subscription can race ahead of the permission and
  # the first delivery fails with "not authorized to invoke function".
  depends_on = [aws_lambda_permission.sns_to_slack_invoke]
}

resource "aws_prometheus_rule_group_namespace" "alerts" {
  workspace_id = aws_prometheus_workspace.this.id
  name         = "${local.name_prefix}-alerts"
  data         = local.alert_rules_yaml
}

resource "aws_prometheus_alert_manager_definition" "this" {
  workspace_id = aws_prometheus_workspace.this.id
  definition   = local.alertmanager_definition
}
