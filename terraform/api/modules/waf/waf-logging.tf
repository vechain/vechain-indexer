data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_cloudwatch_log_group" "waf_log_group" {
  count = var.logs_enable ? 1 : 0

  name              = "aws-waf-logs-${var.env}-${var.project_name}"
  retention_in_days = var.logs_retension
}

resource "aws_wafv2_web_acl_logging_configuration" "waf_logging_cloudwatch" {

  log_destination_configs = [aws_cloudwatch_log_group.waf_log_group[0].arn]
  count                   = var.logs_enable ? 1 : 0

  resource_arn = var.waf_cloudfront_enable ? aws_wafv2_web_acl.waf_cloudfront[0].arn : aws_wafv2_web_acl.rate_limiter[0].arn
  /*
  count = var.logs_enable && var.waf_cloudfront_enable ? 1 : 0
  resource_arn            = aws_wafv2_web_acl.waf_cloudfront[0].arn
 */
  depends_on = [aws_cloudwatch_log_group.waf_log_group]

  dynamic "redacted_fields" {
    for_each = try(var.logging_redacted_fields, [])

    content {
      dynamic "single_header" {
        for_each = redacted_fields.value.all_query_arguments != null ? [1] : []

        content {
          name = redacted_fields.value.single_header
        }
      }

      /*
      dynamic "body" {
        for_each = redacted_fields.value.body != null ? [1] : []

        content {}
      }
     */

      dynamic "method" {
        for_each = redacted_fields.value.method != null ? [1] : []

        content {}
      }

      dynamic "query_string" {
        for_each = redacted_fields.value.query_string != null ? [1] : []

        content {}
      }
      dynamic "single_header" {
        for_each = redacted_fields.value.single_header != null ? [1] : []

        content {
          name = redacted_fields.value.single_header
        }
      }
      /*
      dynamic "single_query_argument" {
        for_each = redacted_fields.value.single_query_argument != null ? [1] : []

        content {
          name = redacted_fields.value.single_query_argument
        }
      }
      */

      dynamic "uri_path" {
        for_each = redacted_fields.value.uri_path != null ? [1] : []

        content {}
      }
    }
  }

  dynamic "logging_filter" {
    for_each = try(var.logging_filter, [])

    content {
      default_behavior = logging_filter.value.default_behavior

      dynamic "filter" {
        for_each = try(logging_filter.value.filter, [])

        content {
          behavior    = filter.value.behavior
          requirement = filter.value.requirement

          dynamic "condition" {
            for_each = try(filter.value.condition, [])

            content {
              dynamic "action_condition" {
                for_each = condition.value.action_condition != null ? [1] : []

                content {
                  action = condition.value.action_condition
                }
              }

              dynamic "label_name_condition" {
                for_each = condition.value.label_name_condition != null ? [1] : []

                content {
                  label_name = condition.value.label_name_condition
                }
              }
            }
          }
        }
      }
    }
  }
}

resource "aws_s3_bucket" "waf_logs" {
  count = var.logs_s3_enable ? 1 : 0

  bucket = "aws-waf-logs-${var.env}-${var.project_name}"

  tags = {
    Name      = "${var.env}-${var.project_name}-ip-set"
    Env       = var.env
    App       = var.project_name
    Terraform = "true"

  }
}



resource "aws_s3_bucket_server_side_encryption_configuration" "lb_logs-encryption" {
  count = var.logs_s3_enable ? 1 : 0

  bucket = aws_s3_bucket.waf_logs[0].bucket

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "lb_logs_public_block" {
  count = var.logs_s3_enable ? 1 : 0

  bucket                  = aws_s3_bucket.waf_logs[0].bucket
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "waf_logs" {
  count = var.logs_s3_enable ? 1 : 0

  bucket = aws_s3_bucket.waf_logs[0].bucket

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
        "Resource" : "arn:aws:s3:::aws-waf-logs-${var.env}-${var.project_name}/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
        "Condition" : {
          "StringEquals" : {
            "s3:x-amz-acl" : "bucket-owner-full-control",
            "aws:SourceAccount" : "${data.aws_caller_identity.current.account_id}"
          },
          "ArnLike" : {
            "aws:SourceArn" : "arn:aws:logs:${data.aws_region.current.id}:${data.aws_caller_identity.current.account_id}:*"
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
        "Resource" : "arn:aws:s3:::aws-waf-logs-${var.env}-${var.project_name}",
        "Condition" : {
          "StringEquals" : {
            "aws:SourceAccount" : "${data.aws_caller_identity.current.account_id}"
          },
          "ArnLike" : {
            "aws:SourceArn" : "arn:aws:logs:${data.aws_region.current.id}:${data.aws_caller_identity.current.account_id}:*"
          }
        }
      }
    ]
  })
}

resource "aws_wafv2_web_acl_logging_configuration" "waf_logging_s3" {
  count = var.logs_s3_enable ? 1 : 0
  /*
  count = var.logs_s3_enable && var.waf_cloudfront_enable ? 1 : 0
  resource_arn            = aws_wafv2_web_acl.waf_cloudfront[0].arn
  */

  resource_arn = var.waf_cloudfront_enable ? aws_wafv2_web_acl.waf_cloudfront[0].arn : aws_wafv2_web_acl.rate_limiter[0].arn

  log_destination_configs = [aws_s3_bucket.waf_logs[0].arn]
  depends_on              = [aws_s3_bucket.waf_logs]

  dynamic "redacted_fields" {
    for_each = try(var.logging_redacted_fields, [])

    content {
      dynamic "single_header" {
        for_each = redacted_fields.value.all_query_arguments != null ? [1] : []

        content {
          name = redacted_fields.value.single_header
        }
      }

      /*
      dynamic "body" {
        for_each = redacted_fields.value.body != null ? [1] : []

        content {}
      }
      */

      dynamic "method" {
        for_each = redacted_fields.value.method != null ? [1] : []

        content {}
      }

      dynamic "query_string" {
        for_each = redacted_fields.value.query_string != null ? [1] : []

        content {}
      }

      dynamic "single_header" {
        for_each = redacted_fields.value.single_header != null ? [1] : []

        content {
          name = redacted_fields.value.single_header
        }
      }

      /*
      dynamic "single_query_argument" {
        for_each = redacted_fields.value.single_query_argument != null ? [1] : []

        content {
          name = redacted_fields.value.single_query_argument
        }
      }
      */

      dynamic "uri_path" {
        for_each = redacted_fields.value.uri_path != null ? [1] : []

        content {}
      }
    }
  }

  dynamic "logging_filter" {
    for_each = try(var.logging_filter, [])

    content {
      default_behavior = logging_filter.value.default_behavior

      dynamic "filter" {
        for_each = try(logging_filter.value.filter, [])

        content {
          behavior    = filter.value.behavior
          requirement = filter.value.requirement

          dynamic "condition" {
            for_each = try(filter.value.condition, [])

            content {
              dynamic "action_condition" {
                for_each = condition.value.action_condition != null ? [1] : []

                content {
                  action = condition.value.action_condition
                }
              }

              dynamic "label_name_condition" {
                for_each = condition.value.label_name_condition != null ? [1] : []

                content {
                  label_name = condition.value.label_name_condition
                }
              }
            }
          }
        }
      }
    }
  }
}
