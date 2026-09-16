resource "aws_iam_role" "ecs_task_execution_role" {
  name               = lower("${var.env}-${var.app_name}-ecs-task-exec-role")
  assume_role_policy = data.aws_iam_policy_document.assume_role_policy.json
}

data "aws_iam_policy_document" "assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_role_policy" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

resource "aws_iam_role_policy" "ecs_task_execution_inline_policy" {
  name   = "${var.env}-${var.project}-${var.app_name}-ecs-policy-inline"
  role   = aws_iam_role.ecs_task_execution_role.id
  policy = data.aws_iam_policy_document.ecs_policy_document.json
}
resource "aws_iam_instance_profile" "ecs_agent" {
  name = "${var.env}-${var.project}-${var.app_name}-ecs-agent"
  role = aws_iam_role.ecs_task_execution_role.name
}

# ECS TASK ROLE
data "aws_iam_policy_document" "ecs_policy_document" {

  statement {
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ConsumeLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = var.secrets_enable ? [1] : []
    content {
      sid    = "SecretsAccess"
      effect = "Allow"
      actions = [
        "secretsmanager:GetSecretValue"
      ]
      resources = ["arn:aws:secretsmanager:*:${data.aws_caller_identity.current.account_id}:secret:*"]
    }
  }

  statement {
    actions   = ["kms:Decrypt"]
    effect    = "Allow"
    resources = ["${var.kms_key_arn != "" ? var.kms_key_arn : "*"}"]
  }

  dynamic "statement" {
    for_each = length(var.sensitive_environment_variables) > 0 ? [1] : []
    content {
      sid    = "SensitiveEnvAccess"
      effect = "Allow"
      actions = [
        "ssm:GetParameter*"
      ]
      resources = [
        for secret in var.sensitive_environment_variables : "arn:aws:ssm:*:*:parameter${secret.valueFrom}"
      ]
    }
  }

  statement {
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel"
    ]
    effect    = "Allow"
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = var.extra_statements != null ? var.extra_statements : []
    content {
      sid       = statement.value.sid
      effect    = statement.value.effect
      actions   = statement.value.actions
      resources = statement.value.resources
    }
  }

}

resource "aws_iam_role_policy" "ecs_role_policy" {
  name   = "${var.env}-${var.project}-${var.app_name}-ecs-policy"
  role   = aws_iam_role.ecs_role.id
  policy = data.aws_iam_policy_document.ecs_policy_document.json
}


resource "aws_iam_role" "ecs_role" {
  name = "${var.env}-${var.project}-${var.app_name}-ecs-role"

  assume_role_policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Action" : "sts:AssumeRole",
        "Principal" : {
          "Service" : ["ecs-tasks.amazonaws.com"]
        },
        "Effect" : "Allow",
        "Sid" : ""
      }
    ]
  })
}