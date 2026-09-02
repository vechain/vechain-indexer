terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

# CLOUDFRONT-scope WAF. The caller must pass the us-east-1 provider.

locals {
  has_bypass_header = var.rate_limit_bypass_header_name != "" && var.rate_limit_bypass_header_value != ""

  tags = merge(
    {
      Name        = var.name
      Env         = var.env
      Environment = var.env
      Workspace   = var.env
      terraform   = "true"
    },
    var.project_tag == null ? {} : { Project = var.project_tag },
  )
}

resource "aws_wafv2_ip_set" "rate_limit_exempt_v4" {
  count = var.enable_rate_limit_exemptions ? 1 : 0

  name               = "${var.name}-rate-limit-exempt-v4"
  description        = "IPv4 CIDRs not counted by the blanket rate rule"
  scope              = "CLOUDFRONT"
  ip_address_version = "IPV4"
  addresses          = var.rate_limit_exempt_ipv4
}

resource "aws_wafv2_ip_set" "rate_limit_exempt_v6" {
  count = var.enable_rate_limit_exemptions ? 1 : 0

  name               = "${var.name}-rate-limit-exempt-v6"
  description        = "IPv6 CIDRs not counted by the blanket rate rule"
  scope              = "CLOUDFRONT"
  ip_address_version = "IPV6"
  addresses          = var.rate_limit_exempt_ipv6
}

resource "aws_wafv2_web_acl" "this" {
  lifecycle {
    precondition {
      condition     = (var.rate_limit_bypass_header_name == "") == (var.rate_limit_bypass_header_value == "")
      error_message = "rate_limit_bypass_header_name and rate_limit_bypass_header_value must both be set or both be empty."
    }
  }

  name        = var.name
  description = "Global WAF managed rules for Cloudfront"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "Imperva-Imperva-IP-Reputation"
    priority = var.rule_priorities["imperva_ip_reputation"]

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "Imperva-IP-Reputation"
        vendor_name = "Imperva"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "Imperva-Imperva-IP-Reputation"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "waf--custom-AWS-block-ip-set"
    priority = var.rule_priorities["block_ip_set"]

    action {
      block {}
    }

    statement {
      ip_set_reference_statement {
        arn = var.block_ip_set_arn
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--custom-AWS-block-ip-set"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "waf--managed-AWS-AWSManagedRulesAmazonIpReputationList"
    priority = var.rule_priorities["aws_ip_reputation"]

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesAmazonIpReputationList"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--managed-AWS-AWSManagedRulesAmazonIpReputationList"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "waf--managed-AWS-AWSManagedRulesCommonRuleSet"
    priority = var.rule_priorities["common_rule_set"]

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"

        # Indexer endpoints take long address/filter query strings.
        rule_action_override {
          name = "SizeRestrictions_QUERYSTRING"
          action_to_use {
            allow {}
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--managed-AWS-AWSManagedRulesCommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "waf--managed-AWS-AWSManagedRulesKnownBadInputsRuleSet"
    priority = var.rule_priorities["known_bad_inputs"]

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--managed-AWS-AWSManagedRulesKnownBadInputsRuleSet"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "waf--managed-AWS-AWSManagedRulesSQLiRuleSet"
    priority = var.rule_priorities["sqli"]

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--managed-AWS-AWSManagedRulesSQLiRuleSet"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "waf--custom-AWS-AWS-Rate-Based-Rule"
    priority = var.rule_priorities["rate_based"]

    action {
      block {}
    }

    statement {
      rate_based_statement {
        aggregate_key_type    = "IP"
        limit                 = var.rate_limit
        evaluation_window_sec = 300

        dynamic "scope_down_statement" {
          for_each = var.enable_rate_limit_exemptions ? [1] : []
          content {
            and_statement {
              statement {
                not_statement {
                  statement {
                    ip_set_reference_statement {
                      arn = aws_wafv2_ip_set.rate_limit_exempt_v4[0].arn
                    }
                  }
                }
              }

              statement {
                not_statement {
                  statement {
                    ip_set_reference_statement {
                      arn = aws_wafv2_ip_set.rate_limit_exempt_v6[0].arn
                    }
                  }
                }
              }

              dynamic "statement" {
                for_each = local.has_bypass_header ? [1] : []
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
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--custom-AWS-AWS-Rate-Based-Rule"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "allow-only-get-options"
    priority = var.rule_priorities["allow_only_get_options"]

    action {
      block {}
    }

    statement {
      not_statement {
        statement {
          regex_match_statement {
            # Unanchored alternation, kept verbatim so the import plans clean.
            regex_string = "^GET|OPTIONS$"

            field_to_match {
              method {}
            }

            text_transformation {
              priority = 0
              type     = "NONE"
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "block-get-options-swagger"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = var.enable_python_ua_rate_limit ? [1] : []
    content {
      name     = "waf--custom-AWS-rate-limit-User-agent-python"
      priority = lookup(var.rule_priorities, "python_ua_rate_limit", 0)

      action {
        block {}
      }

      statement {
        rate_based_statement {
          aggregate_key_type    = "IP"
          limit                 = var.python_ua_rate_limit
          evaluation_window_sec = 300

          scope_down_statement {
            byte_match_statement {
              search_string         = "python-requests"
              positional_constraint = "CONTAINS"

              field_to_match {
                single_header {
                  name = "user-agent"
                }
              }

              text_transformation {
                priority = 0
                type     = "LOWERCASE"
              }
            }
          }
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "waf--custom-AWS-rate-limit-User-agent-python"
        sampled_requests_enabled   = true
      }
    }
  }

  # Deliberately no scope_down: unlike the blanket rule this ignores the
  # exemptions, so the dry run shows every concentrated source.
  dynamic "rule" {
    for_each = var.enable_high_rate_count_rule ? [1] : []
    content {
      name     = "waf--custom-AWS-count-high-rate-ip"
      priority = lookup(var.rule_priorities, "high_rate_count", 0)

      action {
        count {}
      }

      statement {
        rate_based_statement {
          aggregate_key_type    = "IP"
          limit                 = var.high_rate_count_limit
          evaluation_window_sec = 300
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "waf--custom-AWS-count-high-rate-ip"
        sampled_requests_enabled   = true
      }
    }
  }

  rule {
    name     = "waf--custom-AWS-block-health-check-endpoints"
    priority = var.rule_priorities["block_health_check"]

    action {
      block {}
    }

    statement {
      byte_match_statement {
        search_string         = "/actuator"
        positional_constraint = "STARTS_WITH"

        field_to_match {
          uri_path {}
        }

        text_transformation {
          priority = 0
          type     = "NONE"
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "waf--custom-AWS-block-health-check-endpoints"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-cloudwatch"
    sampled_requests_enabled   = false
  }

  tags = local.tags
}

resource "aws_wafv2_web_acl_logging_configuration" "this" {
  resource_arn            = aws_wafv2_web_acl.this.arn
  log_destination_configs = [var.log_group_arn]

  dynamic "logging_filter" {
    for_each = var.logging_filter_block_only ? [1] : []
    content {
      default_behavior = "DROP"

      filter {
        behavior    = "KEEP"
        requirement = "MEETS_ANY"

        condition {
          action_condition {
            action = "BLOCK"
          }
        }

        dynamic "condition" {
          for_each = var.logging_filter_include_count ? [1] : []
          content {
            action_condition {
              action = "COUNT"
            }
          }
        }
      }
    }
  }
}
