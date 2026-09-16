locals {
  enable_flow_log_cloudwatch           = var.enable_flow_log_cloudwatch
  enable_flow_log_s3                   = var.enable_flow_log_s3
  create_flow_log_cloudwatch_iam_role  = local.enable_flow_log_cloudwatch && var.flow_log_destination_type != "s3" && var.create_flow_log_cloudwatch_iam_role
  create_flow_log_cloudwatch_log_group = local.enable_flow_log_cloudwatch && var.flow_log_destination_type != "s3" && var.create_flow_log_cloudwatch_log_group
  create_s3_bucket                     = local.enable_flow_log_s3 && var.flow_log_destination_type == "s3" && var.create_s3_bucket
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

################################################################################
# Flow Log Cloudwatch
################################################################################

resource "aws_flow_log" "vpclogs_cloudwatch" {
  count = local.enable_flow_log_cloudwatch ? 1 : 0

  log_destination_type     = var.flow_log_destination_type
  log_destination          = aws_cloudwatch_log_group.flow_log[0].arn
  log_format               = var.flow_log_log_format
  iam_role_arn             = aws_iam_role.vpc_flow_log_cloudwatch[0].arn
  traffic_type             = var.flow_log_traffic_type
  vpc_id                   = var.vpc_id
  max_aggregation_interval = var.flow_log_max_aggregation_interval

  tags = {
    Environment = var.environment

  }
}

################################################################################
# Flow Log S3
################################################################################

resource "aws_flow_log" "vpclogs_s3" {
  count = local.enable_flow_log_s3 ? 1 : 0

  log_destination_type     = var.flow_log_destination_type
  log_destination          = aws_s3_bucket.vpclogs_bucket[0].arn
  log_format               = var.flow_log_log_format
  traffic_type             = var.flow_log_traffic_type
  vpc_id                   = var.vpc_id
  max_aggregation_interval = var.flow_log_max_aggregation_interval

  dynamic "destination_options" {
    for_each = var.flow_log_destination_type == "s3" ? [true] : []

    content {
      file_format                = var.flow_log_file_format
      hive_compatible_partitions = var.flow_log_hive_compatible_partitions
      per_hour_partition         = var.flow_log_per_hour_partition
    }
  }

  tags = {
    Environment = var.environment

  }
}

################################################################################
# Flow Log group CloudWatch
################################################################################

resource "aws_cloudwatch_log_group" "flow_log" {
  count = local.create_flow_log_cloudwatch_log_group ? 1 : 0

  name              = "${var.environment}-vpc_cloudwatch_log_group"
  retention_in_days = var.flow_log_cloudwatch_log_group_retention_in_days
  kms_key_id        = var.flow_log_cloudwatch_log_group_kms_key_id

  tags = {
    Environment = var.environment
  }
}

################################################################################
# Flow Log Cloudwatch IAM Role
################################################################################

resource "aws_iam_role" "vpc_flow_log_cloudwatch" {
  count = local.create_flow_log_cloudwatch_iam_role ? 1 : 0

  name                 = "${var.environment}-vpc-flow-log-role"
  assume_role_policy   = data.aws_iam_policy_document.flow_log_cloudwatch_assume_role[0].json
  permissions_boundary = var.vpc_flow_log_permissions_boundary

  tags = {
    Environment = var.environment
  }
}

################################################################################
# Flow Log Cloudwatch IAM Role Policy
################################################################################

data "aws_iam_policy_document" "flow_log_cloudwatch_assume_role" {
  count = local.create_flow_log_cloudwatch_iam_role ? 1 : 0

  statement {
    sid = "AWSVPCFlowLogsAssumeRole"

    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }

    effect = "Allow"

    actions = ["sts:AssumeRole"]
  }
}

################################################################################
# Flow Log Cloudwatch IAM Role Policy Attachment
################################################################################

resource "aws_iam_role_policy_attachment" "vpc_flow_log_cloudwatch" {
  count = local.create_flow_log_cloudwatch_iam_role ? 1 : 0

  role       = aws_iam_role.vpc_flow_log_cloudwatch[0].name
  policy_arn = aws_iam_policy.vpc_flow_log_cloudwatch[0].arn
}

################################################################################
# Flow Log Cloudwatch IAM Role Policy
################################################################################

resource "aws_iam_policy" "vpc_flow_log_cloudwatch" {
  count = local.create_flow_log_cloudwatch_iam_role ? 1 : 0

  name   = "${var.environment}-vpc-flow-log-to-cloudwatch"
  policy = data.aws_iam_policy_document.vpc_flow_log_cloudwatch[0].json

}

################################################################################
# Flow Log Cloudwatch IAM Role Policy Document
################################################################################

data "aws_iam_policy_document" "vpc_flow_log_cloudwatch" {
  count = local.create_flow_log_cloudwatch_iam_role ? 1 : 0

  statement {
    sid = "AWSVPCFlowLogsPushToCloudWatch"

    effect = "Allow"

    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
    ]

    resources = ["*"]
  }
}

################################################################################
# s3 bucket for flow logs
################################################################################

resource "aws_s3_bucket" "vpclogs_bucket" {
  count = local.create_s3_bucket && local.enable_flow_log_s3 ? 1 : 0

  bucket = "${var.environment}-${var.app_name}-vpclogs"
}

################################################################################
# s3 bucket policy for flow logs
################################################################################

resource "aws_s3_bucket_policy" "vpc_logs-policy" {
  count  = local.create_s3_bucket ? 1 : 0
  bucket = aws_s3_bucket.vpclogs_bucket[0].bucket

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Id" : "AWSLogDeliveryWrite20150319",
    "Statement" : [
      {
        "Sid" : "AWSLogDeliveryWrite",
        "Effect" : "Allow",
        "Principal" : {
          "Service" : "delivery.logs.amazonaws.com"
        },
        "Action" : "s3:PutObject",
        "Resource" : "arn:aws:s3:::${var.environment}-${var.app_name}-vpclogs/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
        "Condition" : {
          "StringEquals" : {
            "s3:x-amz-acl" : "bucket-owner-full-control",
            "aws:SourceAccount" : "${data.aws_caller_identity.current.account_id}"
          },
          "ArnLike" : {
            "aws:SourceArn" : "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:*"
          }
        }
      },
      {
        "Sid" : "AWSLogDeliveryAclCheck",
        "Effect" : "Allow",
        "Principal" : {
          "Service" : "delivery.logs.amazonaws.com"
        },
        "Action" : "s3:GetBucketAcl",
        "Resource" : "arn:aws:s3:::${var.environment}-${var.app_name}-vpclogs",
        "Condition" : {
          "StringEquals" : {
            "aws:SourceAccount" : "${data.aws_caller_identity.current.account_id}"
          },
          "ArnLike" : {
            "aws:SourceArn" : "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:*"
          }
        }
      }
    ]
  })
}

################################################################################
# s3 bucket encryption for flow logs
################################################################################

resource "aws_s3_bucket_server_side_encryption_configuration" "lb_logs-encryption" {
  count = local.create_s3_bucket ? 1 : 0

  bucket = aws_s3_bucket.vpclogs_bucket[0].bucket

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

################################################################################
# s3 bucket acl for flow logs
################################################################################

resource "aws_s3_bucket_acl" "lb_logs_acl" {
  count = local.create_s3_bucket ? 1 : 0

  bucket = aws_s3_bucket.vpclogs_bucket[0].bucket
  acl    = "private"
}

################################################################################
# s3 bucket public access block for flow logs
################################################################################

resource "aws_s3_bucket_public_access_block" "lb_logs_public_block" {
  count = local.create_s3_bucket ? 1 : 0

  bucket                  = aws_s3_bucket.vpclogs_bucket[0].bucket
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
