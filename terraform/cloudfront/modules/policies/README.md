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
| <a name="input_default_ttl"></a> [default\_ttl](#input\_default\_ttl) | default cache policy time to live | `number` | n/a | yes |
| <a name="input_headers_policy"></a> [headers\_policy](#input\_headers\_policy) | Name of the headers policy | `string` | n/a | yes |
| <a name="input_max_ttl"></a> [max\_ttl](#input\_max\_ttl) | Max cache policy time to live | `number` | n/a | yes |
| <a name="input_min_ttl"></a> [min\_ttl](#input\_min\_ttl) | Min cache policy time to live | `number` | n/a | yes |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_cache_policy_id"></a> [cache\_policy\_id](#output\_cache\_policy\_id) | AWS Cloudfront Cache Policy ID |
| <a name="output_headers_policy_id"></a> [headers\_policy\_id](#output\_headers\_policy\_id) | AWS Cloudfront Response Headers Policy ID |
<!-- END_TF_DOCS -->