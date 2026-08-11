# WAF Module

This module creates AWS WAFv2 Web ACLs for protecting CloudFront distributions (global) and regional resources like ALBs, API Gateways, and AppSync APIs.

## Features

- **Regional WAF (ALB/API Gateway)**: Rate limiting with IP-based exceptions
- **AWS Managed Rules**: AWSManagedRulesCommonRuleSet enabled by default (protection against XSS, SQL injection, etc.)
- **CloudFront WAF**: Managed rule groups with customizable overrides  
- **Resource Association**: Automatically associates WAF with specified resources
- **Logging**: Optional CloudWatch and S3 logging with filtering and field redaction

## Default Protection

When `waf_regional_enable = true`, the module automatically includes the **AWSManagedRulesCommonRuleSet** which provides protection against:
- Cross-site scripting (XSS)
- SQL injection
- Local/remote file inclusion
- Bad bots and crawlers
- Common vulnerabilities (OWASP Top 10)

This can be disabled by setting `enable_aws_managed_common_rules = false` if needed.

## Usage

### Regional WAF with ALB Association

```hcl
module "waf" {
  source = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//waf?ref=<version>"

  env                   = "prod"
  project_name          = "my-app"
  scope                 = "REGIONAL"
  waf_regional_enable   = true
  
  # Enable WAF association with ALBs
  associate_waf         = true
  resource_arn          = [aws_lb.my_alb.arn]
  # Or use: associated_alb_arns = [aws_lb.my_alb.arn]
  
  rate_limit                   = 2000
  rate_limit_exception_list    = ["10.0.0.0/8"]
  managed_rule_group_statement_rules = []
  rate_based_statement_rules   = []
}
```

### CloudFront WAF

```hcl
module "waf_cloudfront" {
  source = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//waf?ref=<version>"

  env                   = "prod"
  project_name          = "my-app"
  scope                 = "CLOUDFRONT"
  waf_cloudfront_enable = true
  global_rule           = "cloudfront-protection"
  
  managed_rule_group_statement_rules = [
    {
      name            = "AWSManagedRulesCommonRuleSet"
      priority        = "1"
      override_action = "none"
      managed_rule_group_statement = [{
        name          = "AWSManagedRulesCommonRuleSet"
        vendor_name   = "AWS"
        excluded_rule = []
      }]
    }
  ]
  rate_based_statement_rules = []
}

# Reference the WAF ARN in your CloudFront distribution
resource "aws_cloudfront_distribution" "cdn" {
  # ...
  web_acl_id = module.waf_cloudfront.waf_cloudfront_arn
}
```

## Backwards Compatibility

For existing implementations that previously created `aws_wafv2_web_acl_association` resources outside the module, you have two options:

1. **Remove external associations**: Delete the external `aws_wafv2_web_acl_association` resource and let the module manage it by setting `associate_waf = true` and providing `resource_arn`.

2. **Keep external associations**: Leave `associate_waf = false` (default) and continue managing associations externally. The module will continue to output the WAF ARN via `waf_limiter_arn`.

<!-- BEGIN_TF_DOCS -->
## Requirements

No requirements.

## Providers

| Name | Version |
|------|---------|
| <a name="provider_aws"></a> [aws](#provider\_aws) | n/a |

## Modules

No modules.

## Resources

| Name | Type |
|------|------|
| [aws_cloudwatch_log_group.waf_log_group](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_group) | resource |
| [aws_s3_bucket.waf_logs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket) | resource |
| [aws_s3_bucket_policy.waf_logs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_policy) | resource |
| [aws_s3_bucket_public_access_block.lb_logs_public_block](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_public_access_block) | resource |
| [aws_s3_bucket_server_side_encryption_configuration.lb_logs-encryption](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_server_side_encryption_configuration) | resource |
| [aws_wafv2_ip_set.rate_limiter_exceptions](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_ip_set) | resource |
| [aws_wafv2_web_acl.rate_limiter](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl) | resource |
| [aws_wafv2_web_acl.waf_cloudfront](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl) | resource |
| [aws_wafv2_web_acl_association.regional_association](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl_association) | resource |
| [aws_wafv2_web_acl_logging_configuration.waf_logging_cloudwatch](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl_logging_configuration) | resource |
| [aws_wafv2_web_acl_logging_configuration.waf_logging_s3](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl_logging_configuration) | resource |
| [aws_caller_identity.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/caller_identity) | data source |
| [aws_region.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/region) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_associate_waf"></a> [associate\_waf](#input\_associate\_waf) | Whether to associate resources (ALBs, API Gateways, etc.) with the WAFv2 ACL. Set to true and provide resource ARNs via resource\_arn or associated\_alb\_arns. | `bool` | `false` | no |
| <a name="input_associated_alb_arns"></a> [associated\_alb\_arns](#input\_associated\_alb\_arns) | Set of alb arns to associate the waf with | `set(string)` | `[]` | no |
| <a name="input_default_action"></a> [default\_action](#input\_default\_action) | n/a | `string` | `"block"` | no |
| <a name="input_enable_aws_managed_common_rules"></a> [enable\_aws\_managed\_common\_rules](#input\_enable\_aws\_managed\_common\_rules) | Enable AWS Managed Rules Common Rule Set by default. Provides protection against common web exploits. | `bool` | `true` | no |
| <a name="input_env"></a> [env](#input\_env) | Deployment environment | `string` | n/a | yes |
| <a name="input_global_rule"></a> [global\_rule](#input\_global\_rule) | Cloudfront WAF Rule Name | `string` | `""` | no |
| <a name="input_logging_filter"></a> [logging\_filter](#input\_logging\_filter) | Filter | <pre>list(object({<br/>    default_behavior = string<br/>    filter = list(object({<br/>      behavior    = string<br/>      requirement = string<br/>      condition = list(object({<br/>        action_condition     = string<br/>        label_name_condition = string<br/>      }))<br/>    }))<br/>  }))</pre> | `[]` | no |
| <a name="input_logging_redacted_fields"></a> [logging\_redacted\_fields](#input\_logging\_redacted\_fields) | Redacted fields | <pre>list(object({<br/>    all_query_arguments   = string<br/>    body                  = string<br/>    method                = string<br/>    query_string          = string<br/>    single_header         = string<br/>    single_query_argument = string<br/>    uri_path              = string<br/>  }))</pre> | `[]` | no |
| <a name="input_logs_enable"></a> [logs\_enable](#input\_logs\_enable) | Enable logs | `bool` | `false` | no |
| <a name="input_logs_retension"></a> [logs\_retension](#input\_logs\_retension) | Specifies the number of days you want to retain log events in the specified log group. Possible values are: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653, and 0. If you select 0, the events in the log group are always retained and never expire. | `number` | `90` | no |
| <a name="input_logs_s3_enable"></a> [logs\_s3\_enable](#input\_logs\_s3\_enable) | Enable logs to destination s3 | `bool` | `false` | no |
| <a name="input_managed_rule_group_statement_rules"></a> [managed\_rule\_group\_statement\_rules](#input\_managed\_rule\_group\_statement\_rules) | n/a | <pre>list(object({<br/>    name            = string<br/>    priority        = string<br/>    override_action = string<br/>    managed_rule_group_statement = list(object({<br/>      name        = string<br/>      vendor_name = string<br/>      excluded_rule = list(object({<br/>        name = string<br/>      }))<br/>    }))<br/>  }))</pre> | n/a | yes |
| <a name="input_positional_constraint"></a> [positional\_constraint](#input\_positional\_constraint) | Positional constraint | `string` | `"EXACTLY"` | no |
| <a name="input_project_name"></a> [project\_name](#input\_project\_name) | Project name | `string` | n/a | yes |
| <a name="input_rate_based_statement_rules"></a> [rate\_based\_statement\_rules](#input\_rate\_based\_statement\_rules) | n/a | <pre>list(object({<br/>    name     = string<br/>    priority = string<br/>    rate_based_statement = object({<br/>      aggregate_key_type = string<br/>      limit              = string<br/>      scope_down_statement = object({<br/>        byte_match_statement = object({<br/>          positional_constraint = string<br/>          search_string         = string<br/>          field_to_match = object({<br/>            uri_path = object({<br/>            })<br/>          })<br/>          text_transformation = object({<br/>            priority = string<br/>            type     = string<br/>          })<br/>        })<br/>      })<br/>    })<br/>  }))</pre> | n/a | yes |
| <a name="input_rate_limit"></a> [rate\_limit](#input\_rate\_limit) | Rate limit for WAFv2 (requests per 5-minute window per IP). Set to 0 to disable rate limiting. | `number` | `0` | no |
| <a name="input_rate_limit_bypass_header_name"></a> [rate\_limit\_bypass\_header\_name](#input\_rate\_limit\_bypass\_header\_name) | Header name whose presence (with matching value) bypasses rate limiting. Leave empty to disable. Must be set together with rate\_limit\_bypass\_header\_value. | `string` | `""` | no |
| <a name="input_rate_limit_bypass_header_value"></a> [rate\_limit\_bypass\_header\_value](#input\_rate\_limit\_bypass\_header\_value) | Expected header value for rate limit bypass. Must be set together with rate\_limit\_bypass\_header\_name. Note: marked sensitive to redact from CLI output, but the value is still stored in plaintext in Terraform state and visible in the AWS WAF console. Treat it as a low-sensitivity token for controlling test traffic, not as a credential. | `string` | `""` | no |
| <a name="input_rate_limit_exception_list"></a> [rate\_limit\_exception\_list](#input\_rate\_limit\_exception\_list) | List of IP CIDR addresses to exclude from rate limiting | `list(string)` | `[]` | no |
| <a name="input_regional_rule"></a> [regional\_rule](#input\_regional\_rule) | Regional WAF Rules for ALB and API Gateway | `string` | `""` | no |
| <a name="input_resource_arn"></a> [resource\_arn](#input\_resource\_arn) | List of resource ARNs (ALBs, API Gateways, AppSync, etc.) to associate with the WAFv2 ACL. Used when associate\_waf is true. | `list(string)` | `[]` | no |
| <a name="input_scope"></a> [scope](#input\_scope) | The scope of this Web ACL. Valid options: CLOUDFRONT, REGIONAL(ALB). | `string` | n/a | yes |
| <a name="input_search_string"></a> [search\_string](#input\_search\_string) | Search string | `string` | `"/charge"` | no |
| <a name="input_waf_cloudfront_enable"></a> [waf\_cloudfront\_enable](#input\_waf\_cloudfront\_enable) | Enable WAF for Cloudfront distribution | `bool` | `false` | no |
| <a name="input_waf_regional_enable"></a> [waf\_regional\_enable](#input\_waf\_regional\_enable) | Enable WAFv2 to ALB, API Gateway or AppSync GraphQL API | `bool` | `false` | no |
| <a name="input_web_acl_id"></a> [web\_acl\_id](#input\_web\_acl\_id) | Specify a web ACL ARN to be associated in CloudFront Distribution / # Optional WEB ACLs (WAF) to attach to CloudFront | `string` | `null` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_waf_cloudfront_arn"></a> [waf\_cloudfront\_arn](#output\_waf\_cloudfront\_arn) | WAF Web ACL ARN for CloudFront (global) |
| <a name="output_waf_limiter_arn"></a> [waf\_limiter\_arn](#output\_waf\_limiter\_arn) | WAF Web ACL rate limiter ARN (regional) |
| <a name="output_waf_regional_association_ids"></a> [waf\_regional\_association\_ids](#output\_waf\_regional\_association\_ids) | Map of resource ARNs to their WAF association IDs |
<!-- END_TF_DOCS -->