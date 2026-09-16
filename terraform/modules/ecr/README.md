# ECR Registry Module

Provides an Elastic Container Registry Repository.

<!-- BEGIN_TF_DOCS -->
## Requirements

| Name | Version |
|------|---------|
| <a name="requirement_terraform"></a> [terraform](#requirement\_terraform) | >= 1.0.0 |
| <a name="requirement_aws"></a> [aws](#requirement\_aws) | >= 4.0.0 |

## Providers

| Name | Version |
|------|---------|
| <a name="provider_aws"></a> [aws](#provider\_aws) | >= 4.0.0 |

## Modules

No modules.

## Resources

| Name | Type |
|------|------|
| [aws_ecr_lifecycle_policy.private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_lifecycle_policy) | resource |
| [aws_ecr_registry_scanning_configuration.configuration](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_registry_scanning_configuration) | resource |
| [aws_ecr_repository.ecr](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository) | resource |
| [aws_ecr_repository_policy.private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository_policy) | resource |
| [aws_iam_policy_document.empty](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_iam_policy_document.resource_full_access_private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_iam_policy_document.resource_private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_iam_policy_document.resource_readonly_access_private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_app_name"></a> [app\_name](#input\_app\_name) | The name of the application | `string` | `"app_name"` | no |
| <a name="input_enable"></a> [enable](#input\_enable) | Enable ECR | `bool` | `true` | no |
| <a name="input_enable_private_ecr"></a> [enable\_private\_ecr](#input\_enable\_private\_ecr) | Enable private ECR | `bool` | `true` | no |
| <a name="input_enable_scan_configuration"></a> [enable\_scan\_configuration](#input\_enable\_scan\_configuration) | Enable ECR registry scanning configuration | `bool` | `false` | no |
| <a name="input_encryption_type"></a> [encryption\_type](#input\_encryption\_type) | Provide type of encryption here | `string` | `"KMS"` | no |
| <a name="input_env"></a> [env](#input\_env) | The environment for the ECR registry | `string` | `null` | no |
| <a name="input_image_tag_mutability"></a> [image\_tag\_mutability](#input\_image\_tag\_mutability) | Provide image mutability | `string` | `"IMMUTABLE"` | no |
| <a name="input_max_image_count"></a> [max\_image\_count](#input\_max\_image\_count) | Provide the maximum number of images | `number` | `10` | no |
| <a name="input_max_untagged_image_count"></a> [max\_untagged\_image\_count](#input\_max\_untagged\_image\_count) | Provide the maximum number of untagged images | `number` | `1` | no |
| <a name="input_principals_full_access"></a> [principals\_full\_access](#input\_principals\_full\_access) | Principal ARN to provide with full access to the ECR. | `list(any)` | `[]` | no |
| <a name="input_principals_readonly_access"></a> [principals\_readonly\_access](#input\_principals\_readonly\_access) | Principal ARN to provide with readonly access to the ECR. | `list(any)` | `[]` | no |
| <a name="input_project"></a> [project](#input\_project) | The name of the project | `string` | `"Project"` | no |
| <a name="input_scan_filter_pattern"></a> [scan\_filter\_pattern](#input\_scan\_filter\_pattern) | Pattern for filtering repositories when enable\_scan\_configuration is true. Use * for wildcard matching | `string` | `"*"` | no |
| <a name="input_scan_filter_type"></a> [scan\_filter\_type](#input\_scan\_filter\_type) | Type of filter pattern when enable\_scan\_configuration is true. Valid values are WILDCARD and PREFIX\_MATCH | `string` | `"WILDCARD"` | no |
| <a name="input_scan_frequency"></a> [scan\_frequency](#input\_scan\_frequency) | Frequency of scanning container images when enable\_scan\_configuration is true. Valid values are SCAN\_ON\_PUSH, CONTINUOUS\_SCAN and MANUAL | `string` | `"SCAN_ON_PUSH"` | no |
| <a name="input_scan_on_push"></a> [scan\_on\_push](#input\_scan\_on\_push) | Indicates whether images are scanned after being pushed to the repository (true) or not scanned (false). | `bool` | `true` | no |
| <a name="input_scan_type"></a> [scan\_type](#input\_scan\_type) | Type of scan to run on container images when enable\_scan\_configuration is true. Valid values are BASIC and ENHANCED | `string` | `"BASIC"` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_ecr_arn"></a> [ecr\_arn](#output\_ecr\_arn) | The ARN of the ECR repository |
| <a name="output_registry_id"></a> [registry\_id](#output\_registry\_id) | The registry ID where the repository was created |
| <a name="output_registry_scan_configuration_id"></a> [registry\_scan\_configuration\_id](#output\_registry\_scan\_configuration\_id) | The ID of the registry scanning configuration |
| <a name="output_repository_url"></a> [repository\_url](#output\_repository\_url) | The URL of the repository (in the form aws\_account\_id.dkr.ecr.region.amazonaws.com/repositoryName) |
| <a name="output_scan_rules"></a> [scan\_rules](#output\_scan\_rules) | The scanning rules configured |
<!-- END_TF_DOCS -->

## How to use

```hcl
module "ecr" {
  source = "git@github.com:vechain/terraform_infrastructure_modules.git//ecr"
  env    = "testing-now"
  project = "ecr-test-poc"

ecr_name = ["frontend-adam", "backend-adam"]

  # ECR base configuration
  image_tag_mutability = "MUTABLE"
  encryption_type = "AES256"
  max_untagged_image_count = 30
  max_image_count = 100

  # Scanning configuration
  enable_scan_configuration = true
  scan_type = "ENHANCED"
  scan_frequency = "CONTINUOUS_SCAN"
  scan_filter_pattern = "*"
  scan_filter_type = "WILDCARD"

  principals_readonly_access = [
    "arn:aws:iam::469359290673:user/aplos_dev"
  ]
  principals_full_access = [
    "arn:aws:iam::469359290673:user/aplos_dev"
  ]
}
```
