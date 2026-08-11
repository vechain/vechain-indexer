# waf integration with regional alb

locals {
  # Combine resource_arn (list) and associated_alb_arns (set) for backwards compatibility
  # This allows existing implementations using either variable to continue working
  all_resource_arns = toset(concat(
    var.resource_arn,
    tolist(var.associated_alb_arns)
  ))

  # Caller-supplied keys stay known at plan time even when the ARNs are not.
  association_targets = length(var.resource_arns) > 0 ? var.resource_arns : {
    for arn in local.all_resource_arns : arn => arn
  }

  has_ip_exceptions = length(var.rate_limit_exception_list) > 0
  has_bypass_header = var.rate_limit_bypass_header_name != "" && var.rate_limit_bypass_header_value != ""
}

resource "aws_wafv2_ip_set" "rate_limiter_exceptions" {
  count = var.waf_regional_enable ? 1 : 0

  name               = "${var.env}-${var.project_name}-ip-set"
  description        = "Rate limiter exceptions"
  scope              = var.scope
  ip_address_version = "IPV4"
  addresses          = var.rate_limit_exception_list

  tags = {
    Name        = "${var.env}-${var.project_name}-ip-set"
    Environment = var.env
    Project     = var.project_name
    Terraform   = "true"
  }
}

resource "aws_wafv2_web_acl" "rate_limiter" {
  count = var.waf_regional_enable ? 1 : 0

  lifecycle {
    precondition {
      condition     = (var.rate_limit_bypass_header_name == "") == (var.rate_limit_bypass_header_value == "")
      error_message = "rate_limit_bypass_header_name and rate_limit_bypass_header_value must both be set or both be empty."
    }
  }

  name        = "${var.env}-${var.project_name}-web-acl"
  scope       = var.scope
  description = "Blanket rate limiter"

  tags = {
    Name        = "${var.env}-${var.project_name}-web-acl"
    Environment = var.env
    Project     = var.project_name
    Terraform   = "true"
  }

  default_action {
    allow {}
  }

  # AWS Managed Rules Common Rule Set - enabled by default for all implementations
  # Provides protection against common web exploits (XSS, SQL injection, etc.)
  dynamic "rule" {
    for_each = var.enable_aws_managed_common_rules ? [1] : []
    content {
      name     = "AWS-AWSManagedRulesCommonRuleSet"
      priority = 0

      override_action {
        none {}
      }

      statement {
        managed_rule_group_statement {
          name        = "AWSManagedRulesCommonRuleSet"
          vendor_name = "AWS"
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "AWS-AWSManagedRulesCommonRuleSet"
        sampled_requests_enabled   = true
      }
    }
  }

  # Simple blanket rate limiting rule - enabled when rate_limit > 0
  # Blocks IPs that exceed the rate limit, with optional IP exceptions
  dynamic "rule" {
    for_each = var.rate_limit > 0 ? [1] : []
    content {
      name     = "blanket-rate-limiter"
      priority = 1

      action {
        block {
          custom_response {
            response_code = 429
          }
        }
      }

      statement {
        rate_based_statement {
          aggregate_key_type = "IP"
          limit              = var.rate_limit

          # Exclude IPs and/or bypass header from rate limiting
          # Case: both IP exceptions AND bypass header
          dynamic "scope_down_statement" {
            for_each = local.has_ip_exceptions && local.has_bypass_header ? [1] : []
            content {
              and_statement {
                statement {
                  not_statement {
                    statement {
                      ip_set_reference_statement {
                        arn = aws_wafv2_ip_set.rate_limiter_exceptions[0].arn
                      }
                    }
                  }
                }
                statement {
                  not_statement {
                    statement {
                      byte_match_statement {
                        search_string         = var.rate_limit_bypass_header_value
                        positional_constraint = "EXACTLY"
                        field_to_match {
                          single_header {
                            name = lower(var.rate_limit_bypass_header_name)
                          }
                        }
                        text_transformation {
                          priority = 0
                          type     = "NONE"
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          # Case: IP exceptions only (no bypass header)
          dynamic "scope_down_statement" {
            for_each = local.has_ip_exceptions && !local.has_bypass_header ? [1] : []
            content {
              not_statement {
                statement {
                  ip_set_reference_statement {
                    arn = aws_wafv2_ip_set.rate_limiter_exceptions[0].arn
                  }
                }
              }
            }
          }

          # Case: bypass header only (no IP exceptions)
          dynamic "scope_down_statement" {
            for_each = !local.has_ip_exceptions && local.has_bypass_header ? [1] : []
            content {
              not_statement {
                statement {
                  byte_match_statement {
                    search_string         = var.rate_limit_bypass_header_value
                    positional_constraint = "EXACTLY"
                    field_to_match {
                      single_header {
                        name = lower(var.rate_limit_bypass_header_name)
                      }
                    }
                    text_transformation {
                      priority = 0
                      type     = "NONE"
                    }
                  }
                }
              }
            }
          }
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "blanket-rate-limiter"
        sampled_requests_enabled   = true
      }
    }
  }

  # Advanced rate-based rules with custom scope_down_statement (legacy support)
  dynamic "rule" {
    for_each = { for rule in var.rate_based_statement_rules : rule.name => rule }
    content {
      name     = "custom-rate-limiter-${rule.value.name}"
      priority = rule.value.priority

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "custom-rate-limiter-${rule.value.name}"
        sampled_requests_enabled   = true
      }

      statement {
        dynamic "rate_based_statement" {
          for_each = lookup(rule.value, "rate_based_statement", null) == null ? [] : [lookup(rule.value, "rate_based_statement")]
          content {
            aggregate_key_type = "IP"
            limit              = var.rate_limit

            dynamic "scope_down_statement" {
              for_each = lookup(rate_based_statement.value, "scope_down_statement", null) == null ? [] : [lookup(rate_based_statement.value, "scope_down_statement")]
              content {
                dynamic "byte_match_statement" {
                  for_each = lookup(scope_down_statement.value, "byte_match_statement", null) == null ? [] : [lookup(scope_down_statement.value, "byte_match_statement")]
                  content {
                    positional_constraint = var.positional_constraint
                    search_string         = var.search_string

                    dynamic "field_to_match" {
                      for_each = lookup(byte_match_statement.value, "field_to_match", null) == null ? [] : [lookup(byte_match_statement.value, "field_to_match")]
                      content {
                        dynamic "uri_path" {
                          for_each = lookup(field_to_match.value, "uri_path", null) == null ? [] : [lookup(field_to_match.value, "uri_path")]
                          content {}
                        }
                      }
                    }
                    dynamic "text_transformation" {
                      for_each = lookup(byte_match_statement.value, "text_transformation")
                      content {
                        priority = 0
                        type     = "NONE"
                      }
                    }
                  }
                }

                dynamic "not_statement" {
                  for_each = lookup(scope_down_statement.value, "not_statement", null) == null ? [] : [lookup(scope_down_statement.value, "not_statement")]
                  content {
                    dynamic "statement" {
                      for_each = lookup(not_statement.value, "statements")
                      content {
                        dynamic "ip_set_reference_statement" {
                          for_each = lookup(statement.value, "ip_set_reference_statement", null) == null ? [] : [lookup(statement.value, "ip_set_reference_statement")]
                          content {
                            arn = aws_wafv2_ip_set.rate_limiter_exceptions[0].arn
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      action {
        block {
          custom_response {
            response_code = 429
          }
        }
      }


    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.env}-${var.project_name}-regional-waf"
    sampled_requests_enabled   = true
  }

}

# Associate the regional WAF Web ACL with ALBs, API Gateways, or other supported resources
# This uses for_each to support multiple resource associations
resource "aws_wafv2_web_acl_association" "regional_association" {
  for_each = var.waf_regional_enable && var.associate_waf ? local.association_targets : {}

  resource_arn = each.value
  web_acl_arn  = aws_wafv2_web_acl.rate_limiter[0].arn

  depends_on = [aws_wafv2_web_acl.rate_limiter]
}
