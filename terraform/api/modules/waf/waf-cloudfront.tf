# waf integration with cloudfront

resource "aws_wafv2_web_acl" "waf_cloudfront" {
  count = var.waf_cloudfront_enable ? 1 : 0

  name        = "${var.env}-${var.project_name}-cloudfront-waf"
  description = "Global WAF managed rules for Cloudfront"
  scope       = var.scope

  default_action {
    allow {}
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.env}-${var.project_name}-cloudfront-waf-cloudwatch"
    sampled_requests_enabled   = false
  }

  dynamic "rule" {

    for_each = { for rule in try(var.managed_rule_group_statement_rules, []) : rule.name => rule }

    content {
      name     = "waf-${var.global_rule}-managed-${rule.value.name}"
      priority = rule.value.priority

      override_action {
        dynamic "count" {
          for_each = lookup(rule.value, "override_action", null) == "count" ? [1] : []
          content {}
        }
        dynamic "none" {
          for_each = lookup(rule.value, "override_action", null) != "count" ? [1] : []
          content {}
        }
      }

      statement {
        dynamic "managed_rule_group_statement" {
          for_each = lookup(rule.value, "managed_rule_group_statement", null) == null ? [] : [lookup(rule.value, "managed_rule_group_statement")]
          content {
            name        = managed_rule_group_statement.value.name
            vendor_name = managed_rule_group_statement.value.vendor_name

            dynamic "rule_action_override" {
              for_each = lookup(managed_rule_group_statement.value, "rule_action_overrides", null) == null ? [] : lookup(managed_rule_group_statement.value, "rule_action_overrides")
              content {
                name = lookup(rule_action_override.value, "name")
                dynamic "action_to_use" {
                  for_each = [lookup(rule_action_override.value, "action_to_use")]
                  content {
                    dynamic "count" {
                      for_each = lookup(action_to_use.value, "count", null) == null ? [] : [lookup(action_to_use.value, "count")]
                      content {}
                    }
                    dynamic "allow" {
                      for_each = lookup(action_to_use.value, "allow", null) == null ? [] : [lookup(action_to_use.value, "allow")]
                      content {}
                    }
                    dynamic "block" {
                      for_each = lookup(action_to_use.value, "block", null) == null ? [] : [lookup(action_to_use.value, "block")]
                      content {}
                    }
                    dynamic "captcha" {
                      for_each = lookup(action_to_use.value, "captcha", null) == null ? [] : [lookup(action_to_use.value, "captcha")]
                      content {}
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
        metric_name                = "waf-${var.global_rule}-managed-${rule.value.name}"
        sampled_requests_enabled   = false
      }
    }
  }

  tags = {
    Name      = "${var.env}-${var.project_name}-cloudfront-waf"
    Env       = var.env
    Project   = var.project_name
    terraform = "true"
  }
}
