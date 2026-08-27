# Non s3 backed Cloudfront Distribution Module

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
| [aws_cloudfront_distribution.non_s3_distribution](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudfront_distribution) | resource |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_api_paths"></a> [api\_paths](#input\_api\_paths) | list of api paths | `list(string)` | n/a | yes |
| <a name="input_cache_policy_id"></a> [cache\_policy\_id](#input\_cache\_policy\_id) | ID of the cache policy | `string` | n/a | yes |
| <a name="input_certificate_arn"></a> [certificate\_arn](#input\_certificate\_arn) | n/a | `string` | n/a | yes |
| <a name="input_cnames"></a> [cnames](#input\_cnames) | List of additional alternate cnames for cache | `list(string)` | `[]` | no |
| <a name="input_distribution_domain"></a> [distribution\_domain](#input\_distribution\_domain) | principal domain to cache | `string` | n/a | yes |
| <a name="input_headers_policy_id"></a> [headers\_policy\_id](#input\_headers\_policy\_id) | ID of the headers policy | `string` | n/a | yes |
| <a name="input_waf_web_acl"></a> [waf\_web\_acl](#input\_waf\_web\_acl) | acl of attached waf | `string` | `""` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_distribution"></a> [distribution](#output\_distribution) | n/a |
| <a name="output_distribution_arn"></a> [distribution\_arn](#output\_distribution\_arn) | n/a |
<!-- END_TF_DOCS -->
