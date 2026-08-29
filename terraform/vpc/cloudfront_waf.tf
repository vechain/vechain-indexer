# Global (us-east-1) and colour-agnostic, so it lives here rather than in
# terraform/api, which applies once per colour and would fight over it.
# These ACLs pre-date the config: it mirrors them exactly so the import plans
# clean. Behaviour changes go through the exemption vars in prod.yml.

locals {
  # Read back from the live ACLs, not normalised: reordering would dirty the
  # import. The two differ only in where block-ip-set sits.
  cloudfront_waf_priorities_mainnet = {
    imperva_ip_reputation  = 1
    block_ip_set           = 2
    aws_ip_reputation      = 3
    common_rule_set        = 4
    known_bad_inputs       = 5
    sqli                   = 6
    rate_based             = 7
    allow_only_get_options = 8
    python_ua_rate_limit   = 9
    block_health_check     = 10
  }

  cloudfront_waf_priorities_testnet = {
    imperva_ip_reputation  = 1
    aws_ip_reputation      = 2
    common_rule_set        = 3
    known_bad_inputs       = 4
    sqli                   = 5
    rate_based             = 6
    allow_only_get_options = 7
    block_ip_set           = 8
    block_health_check     = 9
  }

  cloudfront_waf_needs_bypass_token = anytrue([
    for net in ["mainnet", "testnet"] :
    local.env.cloudfront_waf[net].enable_rate_limit_exemptions
    && lookup(local.env.cloudfront_waf[net], "rate_limit_bypass_header_name", "") != ""
  ])
}

# Unmanaged on purpose: edited by hand during incidents.
data "aws_wafv2_ip_set" "cloudfront_block_list" {
  provider = aws.us_east_1

  name  = "Ip-block-list"
  scope = "CLOUDFRONT"
}

data "aws_cloudwatch_log_group" "waf_cloudfront_mainnet" {
  provider = aws.us_east_1

  name = "aws-waf-logs-veworld-cloudfront"
}

data "aws_cloudwatch_log_group" "waf_cloudfront_testnet" {
  provider = aws.us_east_1

  name = "aws-waf-logs-veworld-testnet-cloudfront"
}

module "cloudfront_waf_mainnet" {
  source = "./modules/cloudfront-waf"

  providers = {
    aws = aws.us_east_1
  }

  name = "shared-veworld-cloudfront-waf"
  # Project tag comes from the provider default_tags (veworld).

  rate_limit                  = local.env.cloudfront_waf.mainnet.rate_limit
  rule_priorities             = local.cloudfront_waf_priorities_mainnet
  block_ip_set_arn            = data.aws_wafv2_ip_set.cloudfront_block_list.arn
  log_group_arn               = data.aws_cloudwatch_log_group.waf_cloudfront_mainnet.arn
  logging_filter_block_only   = true
  enable_python_ua_rate_limit = true

  enable_rate_limit_exemptions   = local.env.cloudfront_waf.mainnet.enable_rate_limit_exemptions
  rate_limit_exempt_ipv4         = local.env.cloudfront_waf.mainnet.rate_limit_exempt_ipv4
  rate_limit_exempt_ipv6         = local.env.cloudfront_waf.mainnet.rate_limit_exempt_ipv6
  rate_limit_bypass_header_name  = local.env.cloudfront_waf.mainnet.enable_rate_limit_exemptions ? lookup(local.env.cloudfront_waf.mainnet, "rate_limit_bypass_header_name", "") : ""
  rate_limit_bypass_header_value = local.env.cloudfront_waf.mainnet.enable_rate_limit_exemptions ? try(data.aws_secretsmanager_secret_version.waf_rate_limit_bypass_token[0].secret_string, "") : ""
}

module "cloudfront_waf_testnet" {
  source = "./modules/cloudfront-waf"

  providers = {
    aws = aws.us_east_1
  }

  name        = "shared-veworld-testnet-cloudfront-waf"
  project_tag = "veworld-testnet"

  rate_limit                  = local.env.cloudfront_waf.testnet.rate_limit
  rule_priorities             = local.cloudfront_waf_priorities_testnet
  block_ip_set_arn            = data.aws_wafv2_ip_set.cloudfront_block_list.arn
  log_group_arn               = data.aws_cloudwatch_log_group.waf_cloudfront_testnet.arn
  logging_filter_block_only   = false
  enable_python_ua_rate_limit = false

  enable_rate_limit_exemptions   = local.env.cloudfront_waf.testnet.enable_rate_limit_exemptions
  rate_limit_exempt_ipv4         = local.env.cloudfront_waf.testnet.rate_limit_exempt_ipv4
  rate_limit_exempt_ipv6         = local.env.cloudfront_waf.testnet.rate_limit_exempt_ipv6
  rate_limit_bypass_header_name  = local.env.cloudfront_waf.testnet.enable_rate_limit_exemptions ? lookup(local.env.cloudfront_waf.testnet, "rate_limit_bypass_header_name", "") : ""
  rate_limit_bypass_header_value = local.env.cloudfront_waf.testnet.enable_rate_limit_exemptions ? try(data.aws_secretsmanager_secret_version.waf_rate_limit_bypass_token[0].secret_string, "") : ""
}

# Created by terraform/api; #1531 left CloudFront the only layer matching it.
data "aws_secretsmanager_secret_version" "waf_rate_limit_bypass_token" {
  count = local.cloudfront_waf_needs_bypass_token ? 1 : 0

  secret_id = "/prod/${var.project}/waf-rate-limit-bypass-token"
}

# Remove once the first apply has adopted these.
import {
  to = module.cloudfront_waf_mainnet.aws_wafv2_web_acl.this
  id = "00d48f62-7c03-413e-9771-9829ab803197/shared-veworld-cloudfront-waf/CLOUDFRONT"
}

import {
  to = module.cloudfront_waf_mainnet.aws_wafv2_web_acl_logging_configuration.this
  id = "arn:aws:wafv2:us-east-1:905964754131:global/webacl/shared-veworld-cloudfront-waf/00d48f62-7c03-413e-9771-9829ab803197"
}

import {
  to = module.cloudfront_waf_testnet.aws_wafv2_web_acl.this
  id = "43ca3a09-6624-4dd0-a8b3-8c7ca24036dc/shared-veworld-testnet-cloudfront-waf/CLOUDFRONT"
}

import {
  to = module.cloudfront_waf_testnet.aws_wafv2_web_acl_logging_configuration.this
  id = "arn:aws:wafv2:us-east-1:905964754131:global/webacl/shared-veworld-testnet-cloudfront-waf/43ca3a09-6624-4dd0-a8b3-8c7ca24036dc"
}

output "cloudfront_waf_mainnet_arn" {
  value = module.cloudfront_waf_mainnet.web_acl_arn
}

output "cloudfront_waf_testnet_arn" {
  value = module.cloudfront_waf_testnet.web_acl_arn
}
