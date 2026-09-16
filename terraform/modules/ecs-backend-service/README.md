# ECS Backend Service  Module

 Provides an Elastic Service Infrastructure without Loadbalancer. It contains the below components.

- ECR cluster creation.
- ECR repo creation for services.
- ECS service standalone.
- Secrets & kms creation and association to services conditionally.
- Servicediscovery.

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
| [aws_cloudwatch_log_group.ecs_cw_log_group](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_group) | resource |
| [aws_cloudwatch_log_metric_filter.ecs_cw_log_metric_filter](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_metric_filter) | resource |
| [aws_ecr_repository.repo](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository) | resource |
| [aws_ecs_service.service](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecs_service) | resource |
| [aws_ecs_task_definition.ecs_task_definition](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecs_task_definition) | resource |
| [aws_iam_instance_profile.ecs_agent](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_instance_profile) | resource |
| [aws_iam_role.ecs_role](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role) | resource |
| [aws_iam_role.ecs_task_execution_role](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role) | resource |
| [aws_iam_role_policy.ecs_role_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy) | resource |
| [aws_iam_role_policy.ecs_task_execution_inline_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy) | resource |
| [aws_iam_role_policy_attachment.ecs_task_execution_role_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment) | resource |
| [aws_secretsmanager_secret.secrets](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/secretsmanager_secret) | resource |
| [aws_service_discovery_service.service](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/service_discovery_service) | resource |
| [aws_caller_identity.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/caller_identity) | data source |
| [aws_iam_policy_document.assume_role_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_iam_policy_document.ecs_policy_document](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_partition.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/partition) | data source |
| [aws_region.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/region) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_additional_containers"></a> [additional\_containers](#input\_additional\_containers) | n/a | <pre>list(object({<br/>    name   = string<br/>    image  = string<br/>    cpu    = optional(number, null)<br/>    memory = optional(number, null)<br/>    environment = list(object({<br/>      name  = string<br/>      value = string<br/>    }))<br/>    command = optional(list(string), [])<br/>    secrets = optional(list(object({<br/>      name      = string<br/>      valueFrom = string<br/>    })), [])<br/>    portMappings = optional(list(object({<br/>      containerPort = number<br/>      hostPort      = number<br/>      name          = string<br/>      protocol      = string<br/>    })), [])<br/>    healthCheck = optional(object({<br/>      command     = list(string)<br/>      interval    = number<br/>      retries     = number<br/>      startPeriod = number<br/>      timeout     = number<br/>    }), null)<br/>    dependsOn = optional(list(object({<br/>      containerName = string<br/>      condition     = string<br/>    })))<br/>    logConfiguration = optional(object({<br/>      logDriver = optional(string, "awslogs")<br/>      options   = optional(map(string), {})<br/>    }), null)<br/>  }))</pre> | `[]` | no |
| <a name="input_additional_port_mappings"></a> [additional\_port\_mappings](#input\_additional\_port\_mappings) | Additional port mappings for the container | <pre>list(object({<br/>    containerPort = number<br/>    hostPort      = number<br/>    protocol      = string<br/>    appProtocol   = string<br/>  }))</pre> | `[]` | no |
| <a name="input_additional_secrets"></a> [additional\_secrets](#input\_additional\_secrets) | A map of additional secret names to their ARN values | `map(string)` | `{}` | no |
| <a name="input_app_name"></a> [app\_name](#input\_app\_name) | n/a | `string` | n/a | yes |
| <a name="input_cidr"></a> [cidr](#input\_cidr) | n/a | `string` | `""` | no |
| <a name="input_cloudmap_ttl"></a> [cloudmap\_ttl](#input\_cloudmap\_ttl) | n/a | `number` | `300` | no |
| <a name="input_cluster"></a> [cluster](#input\_cluster) | n/a | `string` | `""` | no |
| <a name="input_command"></a> [command](#input\_command) | The command arguments that are passed to the container entrypoint | `list(string)` | `[]` | no |
| <a name="input_containerPort"></a> [containerPort](#input\_containerPort) | n/a | `number` | n/a | yes |
| <a name="input_container_name"></a> [container\_name](#input\_container\_name) | n/a | `string` | `""` | no |
| <a name="input_container_port"></a> [container\_port](#input\_container\_port) | n/a | `string` | `""` | no |
| <a name="input_cpu"></a> [cpu](#input\_cpu) | n/a | `number` | n/a | yes |
| <a name="input_deployment_maximum_percent"></a> [deployment\_maximum\_percent](#input\_deployment\_maximum\_percent) | n/a | `number` | `200` | no |
| <a name="input_deployment_minimum_healthy_percent"></a> [deployment\_minimum\_healthy\_percent](#input\_deployment\_minimum\_healthy\_percent) | n/a | `number` | `100` | no |
| <a name="input_desired_capacity"></a> [desired\_capacity](#input\_desired\_capacity) | n/a | `number` | `1` | no |
| <a name="input_ecr_image_tag"></a> [ecr\_image\_tag](#input\_ecr\_image\_tag) | n/a | `string` | `""` | no |
| <a name="input_ecr_repo_uri"></a> [ecr\_repo\_uri](#input\_ecr\_repo\_uri) | n/a | `string` | `""` | no |
| <a name="input_enable_execute_command"></a> [enable\_execute\_command](#input\_enable\_execute\_command) | n/a | `bool` | `true` | no |
| <a name="input_env"></a> [env](#input\_env) | n/a | `string` | `""` | no |
| <a name="input_environment_variables"></a> [environment\_variables](#input\_environment\_variables) | n/a | <pre>list(object({<br/>    name  = string<br/>    value = string<br/>  }))</pre> | n/a | yes |
| <a name="input_execution_role_arn"></a> [execution\_role\_arn](#input\_execution\_role\_arn) | n/a | `string` | `""` | no |
| <a name="input_extra_statements"></a> [extra\_statements](#input\_extra\_statements) | Additional IAM policy statements | <pre>list(object({<br/>    sid       = string<br/>    effect    = string<br/>    actions   = list(string)<br/>    resources = list(string)<br/>  }))</pre> | `null` | no |
| <a name="input_force_new_deployment"></a> [force\_new\_deployment](#input\_force\_new\_deployment) | force\_new\_deployment | `string` | `false` | no |
| <a name="input_health_check_grace_period_seconds"></a> [health\_check\_grace\_period\_seconds](#input\_health\_check\_grace\_period\_seconds) | Seconds to ignore failing health checks after a task starts. Requires a service registry or load balancer. | `number` | `0` | no |
| <a name="input_healthcheck"></a> [healthcheck](#input\_healthcheck) | n/a | <pre>object({<br/>    command     = optional(list(string))<br/>    interval    = optional(number)<br/>    retries     = optional(number)<br/>    start_delay = optional(number)<br/>    timeout     = optional(number)<br/>  })</pre> | `null` | no |
| <a name="input_hostPort"></a> [hostPort](#input\_hostPort) | n/a | `number` | n/a | yes |
| <a name="input_is_create_repo"></a> [is\_create\_repo](#input\_is\_create\_repo) | n/a | `bool` | `true` | no |
| <a name="input_kms_key_arn"></a> [kms\_key\_arn](#input\_kms\_key\_arn) | n/a | `string` | `"*"` | no |
| <a name="input_kms_key_id"></a> [kms\_key\_id](#input\_kms\_key\_id) | n/a | `string` | `""` | no |
| <a name="input_launch_type"></a> [launch\_type](#input\_launch\_type) | n/a | `string` | `"FARGATE"` | no |
| <a name="input_log_metric_filters"></a> [log\_metric\_filters](#input\_log\_metric\_filters) | Map of metric filters to create | <pre>list(object({<br/>    name    = string<br/>    pattern = string<br/>  }))</pre> | `[]` | no |
| <a name="input_main_cpu"></a> [main\_cpu](#input\_main\_cpu) | n/a | `number` | `null` | no |
| <a name="input_main_memory"></a> [main\_memory](#input\_main\_memory) | n/a | `number` | `null` | no |
| <a name="input_max_size"></a> [max\_size](#input\_max\_size) | n/a | `string` | `""` | no |
| <a name="input_memory"></a> [memory](#input\_memory) | n/a | `number` | n/a | yes |
| <a name="input_min_size"></a> [min\_size](#input\_min\_size) | n/a | `string` | `""` | no |
| <a name="input_namespace_id"></a> [namespace\_id](#input\_namespace\_id) | n/a | `string` | n/a | yes |
| <a name="input_network"></a> [network](#input\_network) | Value for network the service is running on | `string` | `null` | no |
| <a name="input_project"></a> [project](#input\_project) | n/a | `string` | `""` | no |
| <a name="input_readonly_root_filesystem"></a> [readonly\_root\_filesystem](#input\_readonly\_root\_filesystem) | Whether containers should have read-only access to the root filesystem | `bool` | `false` | no |
| <a name="input_region"></a> [region](#input\_region) | n/a | `string` | n/a | yes |
| <a name="input_runtime_platform"></a> [runtime\_platform](#input\_runtime\_platform) | runtime platform | <pre>list(object({<br/>    operating_system_family = string<br/>    cpu_architecture        = string<br/>  }))</pre> | <pre>[<br/>  {<br/>    "cpu_architecture": "ARM64",<br/>    "operating_system_family": "LINUX"<br/>  }<br/>]</pre> | no |
| <a name="input_scan_all_repo_images"></a> [scan\_all\_repo\_images](#input\_scan\_all\_repo\_images) | Scan all pushed images to each repository in ECR | `bool` | `true` | no |
| <a name="input_secrets_enable"></a> [secrets\_enable](#input\_secrets\_enable) | n/a | `bool` | `false` | no |
| <a name="input_security_groups"></a> [security\_groups](#input\_security\_groups) | The security groups to attach to the load balancer. e.g. ["sg-edcd9784","sg-edcd9785"] | `list(string)` | `[]` | no |
| <a name="input_sensitive_environment_variables"></a> [sensitive\_environment\_variables](#input\_sensitive\_environment\_variables) | n/a | <pre>list(object({<br/>    name      = string<br/>    valueFrom = string<br/>  }))</pre> | `[]` | no |
| <a name="input_service_discovery_name"></a> [service\_discovery\_name](#input\_service\_discovery\_name) | n/a | `string` | `""` | no |
| <a name="input_subnets"></a> [subnets](#input\_subnets) | A list of subnets for the ecs service. | `list(string)` | `null` | no |
| <a name="input_vpc_id"></a> [vpc\_id](#input\_vpc\_id) | VPC id where the load balancer and other resources will be deployed. | `string` | `null` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_cluster_name"></a> [cluster\_name](#output\_cluster\_name) | The ECS cluster name |
| <a name="output_ecs_cloudwatch_log_group_name"></a> [ecs\_cloudwatch\_log\_group\_name](#output\_ecs\_cloudwatch\_log\_group\_name) | The name of the CloudWatch log group for the ECS service |
| <a name="output_log_metric_names"></a> [log\_metric\_names](#output\_log\_metric\_names) | The names of the metric filters |
| <a name="output_service_name"></a> [service\_name](#output\_service\_name) | The ECS service name |
<!-- END_TF_DOCS -->

## How to use

```hcl
module "ecs-backend-service-chain-scanner" {
  source           = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs-backend-service?ref=v.1.0.9"
  vpc_id           = var.vpc_id
  region           = var.region
  cluster          = var.cluster
  subnets          = var.subnets
  env              = var.environment
  is_create_repo   = false # true if you are wishing to create ECR repos
  ecr_repo_uri     = var.ecr_repo_uri
  secrets_enable   = false # true if you are wishing to create secrets
  ecr_image_tag    = var.ecr_image_tag
  commands         = var.commands
  app_name         = var.app_name
  project          = var.project
  cpu              = var.cpu
  memory           = var.memory
  cidr             = var.cidr
  security_groups  = [aws_security_group.ecs_service_sg.id]
  desired_capacity = var.desired_capacity
  containerPort    = 8080
  hostPort         = 8080
  namespace_id     = var.namespace_id
  healthCheck      = var.healthCheck
  log_metric_filters = [
    {
      name    = "AppUnhealthy",
      pattern = "Application is UNHEALTHY"
    }
  ]

  environment_variables = [
  ]
  
  # Enhanced security - read-only root filesystem
  readonly_root_filesystem = true
}
```
