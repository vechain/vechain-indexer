# Cloudfront Policies Module

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
| [aws_cloudfront_cache_policy.cache_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudfront_cache_policy) | resource |
| [aws_cloudfront_response_headers_policy.header_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudfront_response_headers_policy) | resource |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_cache_policy"></a> [cache\_policy](#input\_cache\_policy) | Name of the cache policy | `string` | n/a | yes |
| <a name="input_cookie_behavior"></a> [cookie\_behavior](#input\_cookie\_behavior) | cookie behavior | `string` | `"none"` | no |
| <a name="input_create_header_policy"></a> [create\_header\_policy](#input\_create\_header\_policy) | n/a | `number` | `0` | no |
| <a name="input_default_ttl"></a> [default\_ttl](#input\_default\_ttl) | default cache policy time to live | `number` | n/a | yes |
| <a name="input_enable_brotli"></a> [enable\_brotli](#input\_enable\_brotli) | n/a | `bool` | `false` | no |
| <a name="input_enable_gzip"></a> [enable\_gzip](#input\_enable\_gzip) | n/a | `bool` | `false` | no |
| <a name="input_header_behavior"></a> [header\_behavior](#input\_header\_behavior) | header behavior | `string` | `"none"` | no |
| <a name="input_headers_policy"></a> [headers\_policy](#input\_headers\_policy) | Name of the headers policy | `string` | `"default_headers_policy"` | no |
| <a name="input_max_ttl"></a> [max\_ttl](#input\_max\_ttl) | Max cache policy time to live | `number` | n/a | yes |
| <a name="input_min_ttl"></a> [min\_ttl](#input\_min\_ttl) | Min cache policy time to live | `number` | n/a | yes |
| <a name="input_query_string_behavior"></a> [query\_string\_behavior](#input\_query\_string\_behavior) | query string behavior | `string` | `"none"` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_cache_policy_id"></a> [cache\_policy\_id](#output\_cache\_policy\_id) | AWS Cloudfront Cache Policy ID |
| <a name="output_headers_policy_id"></a> [headers\_policy\_id](#output\_headers\_policy\_id) | AWS Cloudfront Response Headers Policy ID |
<!-- END_TF_DOCS -->