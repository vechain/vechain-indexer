# Backup inventory — RDS snapshot facts as CloudWatch metrics, polled from the shared stack.
#
# The instances live in terraform/api, one set per colour, and a dead colour is restored from
# the live colour's newest snapshot. Polling from here means the inventory survives a colour
# being torn down and rebuilt, and covers both colours from one place. The staleness alarm is
# per instance and stays with its siblings in terraform/api/alarms.tf.

locals {
  pg_backup_namespace = "VeWorld/RDSBackups"
  pg_backup_tag_key   = "Backup"
  # Set by terraform/api/postgres.tf from its own var.project ("veworld"), not this stack's.
  pg_backup_tag_value = "veworld-pg"
  pg_backup_schedule  = "rate(5 minutes)"
}

resource "aws_cloudwatch_log_group" "pg_backup_inventory" {
  name              = "/aws/lambda/${local.name_prefix}-pg-backup-inventory"
  retention_in_days = local.env.log_retention_days
}

data "aws_iam_policy_document" "pg_backup_inventory_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "pg_backup_inventory" {
  name               = "${local.name_prefix}-pg-backup-inventory"
  assume_role_policy = data.aws_iam_policy_document.pg_backup_inventory_assume.json
}

resource "aws_iam_role_policy_attachment" "pg_backup_inventory_basic" {
  role       = aws_iam_role.pg_backup_inventory.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "pg_backup_inventory" {
  # These three Describe calls take no resource-level permissions; the instances are narrowed
  # by tag in the function, which is a filter rather than a grant.
  statement {
    sid    = "ReadBackupInventory"
    effect = "Allow"
    actions = [
      "rds:DescribeDBInstances",
      "rds:DescribeDBSnapshots",
      "rds:DescribeEvents",
    ]
    resources = ["*"]
  }

  # PutMetricData is likewise resource-less; the namespace condition is what scopes it.
  statement {
    sid       = "PublishBackupMetrics"
    effect    = "Allow"
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = [local.pg_backup_namespace]
    }
  }
}

resource "aws_iam_role_policy" "pg_backup_inventory" {
  name   = "${local.name_prefix}-pg-backup-inventory"
  role   = aws_iam_role.pg_backup_inventory.id
  policy = data.aws_iam_policy_document.pg_backup_inventory.json
}

data "archive_file" "pg_backup_inventory" {
  type        = "zip"
  source_file = "${path.module}/lambda/pg_backup_inventory.py"
  output_path = "${path.module}/.terraform/pg_backup_inventory.zip"
}

resource "aws_lambda_function" "pg_backup_inventory" {
  function_name    = "${local.name_prefix}-pg-backup-inventory"
  role             = aws_iam_role.pg_backup_inventory.arn
  filename         = data.archive_file.pg_backup_inventory.output_path
  source_code_hash = data.archive_file.pg_backup_inventory.output_base64sha256
  runtime          = "python3.12"
  handler          = "pg_backup_inventory.handler"
  timeout          = 60
  memory_size      = 256

  environment {
    variables = {
      METRIC_NAMESPACE = local.pg_backup_namespace
      BACKUP_TAG_KEY   = local.pg_backup_tag_key
      BACKUP_TAG_VALUE = local.pg_backup_tag_value
    }
  }

  depends_on = [aws_cloudwatch_log_group.pg_backup_inventory]
}

resource "aws_cloudwatch_event_rule" "pg_backup_inventory" {
  name                = "${local.name_prefix}-pg-backup-inventory"
  description         = "Polls RDS snapshot state into ${local.pg_backup_namespace}."
  schedule_expression = local.pg_backup_schedule
}

resource "aws_cloudwatch_event_target" "pg_backup_inventory" {
  rule = aws_cloudwatch_event_rule.pg_backup_inventory.name
  arn  = aws_lambda_function.pg_backup_inventory.arn
}

resource "aws_lambda_permission" "pg_backup_inventory" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.pg_backup_inventory.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.pg_backup_inventory.arn
}

# One alarm covers both ways the inventory can go quiet, because the handler publishes nothing
# when it throws: a run that fails and a schedule that stopped both read as missing data. The
# per-instance staleness alarm treats missing data as "hold", so this is what makes that safe.
resource "aws_cloudwatch_metric_alarm" "pg_backup_inventory_stalled" {
  alarm_name        = "${local.name_prefix}-pg-backup-inventory-stalled"
  alarm_description = "[${terraform.workspace}/shared/all] backups: Backup inventory stalled — no Postgres instance has been inventoried for 30 minutes, so the per-instance backup alarms are blind."

  namespace           = local.pg_backup_namespace
  metric_name         = "InstancesInventoried"
  statistic           = "Maximum"
  period              = 900
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "LessThanThreshold"
  threshold           = 1
  treat_missing_data  = "breaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}
