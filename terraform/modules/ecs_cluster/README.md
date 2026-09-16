# ECS Cluster Module
 Provides an Elastic cluster.

<!-- BEGIN_TF_DOCS -->
## Requirements

No requirements.

## Providers

| Name | Version |
|------|---------|
| <a name="provider_aws"></a> [aws](#provider\_aws) | n/a |

## Modules

| Name | Source | Version |
|------|--------|---------|
| <a name="module_ecr"></a> [ecr](#module\_ecr) | git::git@github.com:vechain/terraform_infrastructure_modules.git//ecr | n/a |

## Resources

| Name | Type |
|------|------|
| [aws_ecs_cluster.ecs_cluster](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecs_cluster) | resource |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_app_name"></a> [app\_name](#input\_app\_name) | The name of the application | `string` | `"app"` | no |
| <a name="input_cidr"></a> [cidr](#input\_cidr) | n/a | `string` | `""` | no |
| <a name="input_enable_container_insights"></a> [enable\_container\_insights](#input\_enable\_container\_insights) | Enable container insights for the ECS cluster | `bool` | `true` | no |
| <a name="input_env"></a> [env](#input\_env) | The environment in which the infrastructure is deployed | `string` | n/a | yes |
| <a name="input_is_create_repo"></a> [is\_create\_repo](#input\_is\_create\_repo) | Whether to create ECR repository | `bool` | `false` | no |
| <a name="input_project"></a> [project](#input\_project) | The name of the project | `string` | `""` | no |
| <a name="input_scan_all_repo_images"></a> [scan\_all\_repo\_images](#input\_scan\_all\_repo\_images) | Scan all pushed images to each repository in ECR | `bool` | `true` | no |
| <a name="input_vpc_id"></a> [vpc\_id](#input\_vpc\_id) | n/a | `string` | `""` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_arn"></a> [arn](#output\_arn) | ECS cluster arn |
| <a name="output_id"></a> [id](#output\_id) | ECS cluster id |
| <a name="output_name"></a> [name](#output\_name) | ECS cluster name |
<!-- END_TF_DOCS -->

## How to use
 ```
 module "ecs-cluster" {
  source  = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs_cluster?ref=v.1.0.4"
  env     = var.environment
  project = var.project
  vpc_id  = var.vpc_id
  cidr    = var.cidr
}
```
