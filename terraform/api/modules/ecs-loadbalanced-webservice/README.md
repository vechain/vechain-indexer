# ECS Loadbalanced WebService  Module

> **Vendored copy.** This module was copied verbatim from
> `vechain/terraform_infrastructure_modules//ecs-loadbalanced-webservice` at tag
> `v.3.1.28` to remove the remote git module dependency for the API stack.
> There is no functional divergence from upstream; it is a faithful copy kept
> local so the stack no longer resolves a remote git module.

Provides an Elastic Service Infrastructure with Loadbalancer. It contains the below components.

- ECR cluster creation.
- ECR repo creation for services.
- ECS service load-balanced.
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

No modules.

## Resources

| Name | Type |
|------|------|
| [aws_acm_certificate.nscert](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/acm_certificate) | resource |
| [aws_alb.alb](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb) | resource |
| [aws_alb_listener.alb_listener](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener) | resource |
| [aws_alb_listener.alb_listener_https](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener) | resource |
| [aws_alb_listener_rule.listener_rule](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener_rule) | resource |
| [aws_alb_listener_rule.listener_rule_1](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener_rule) | resource |
| [aws_alb_listener_rule.listener_rule_2](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener_rule) | resource |
| [aws_alb_listener_rule.listener_rule_3](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener_rule) | resource |
| [aws_alb_listener_rule.listener_rule_4](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_listener_rule) | resource |
| [aws_alb_target_group.alb_target_group_https](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_target_group) | resource |
| [aws_alb_target_group.nlb_target_group_tcp](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_target_group) | resource |
| [aws_alb_target_group.tg_1](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_target_group) | resource |
| [aws_alb_target_group.tg_2](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/alb_target_group) | resource |
| [aws_appautoscaling_policy.ecs_service_cpu_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/appautoscaling_policy) | resource |
| [aws_appautoscaling_policy.ecs_service_memory_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/appautoscaling_policy) | resource |
| [aws_appautoscaling_target.ecs_target](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/appautoscaling_target) | resource |
| [aws_cloudwatch_log_group.ecs_cw_log_group](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_group) | resource |
| [aws_cloudwatch_log_metric_filter.ecs_cw_log_metric_filter](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_metric_filter) | resource |
| [aws_ecr_registry_scanning_configuration.configuration](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_registry_scanning_configuration) | resource |
| [aws_ecr_repository.repo](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository) | resource |
| [aws_ecs_service.service_alb](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecs_service) | resource |
| [aws_ecs_task_definition.ecs_task_definition](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecs_task_definition) | resource |
| [aws_iam_instance_profile.ecs_agent](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_instance_profile) | resource |
| [aws_iam_role.ecs_role](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role) | resource |
| [aws_iam_role.ecs_task_execution_role](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role) | resource |
| [aws_iam_role_policy.ecs_role_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy) | resource |
| [aws_iam_role_policy.ecs_task_execution_inline_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy) | resource |
| [aws_iam_role_policy_attachment.ecs_task_execution_role_policy_EC2](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment) | resource |
| [aws_iam_role_policy_attachment.ecs_task_execution_role_policy_ECS](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment) | resource |
| [aws_lb_listener.tcp](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lb_listener) | resource |
| [aws_route53_record.nscertvalidators](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_record) | resource |
| [aws_route53_record.private_domain-ns](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_record) | resource |
| [aws_route53_record.public_zone](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_record) | resource |
| [aws_route53_zone.private](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_zone) | resource |
| [aws_route53_zone.public](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_zone) | resource |
| [aws_s3_bucket.lb_logs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket) | resource |
| [aws_s3_bucket_acl.lb_logs_acl](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_acl) | resource |
| [aws_s3_bucket_lifecycle_configuration.lb_logs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_lifecycle_configuration) | resource |
| [aws_s3_bucket_ownership_controls.lb_logs_acl_ownership](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_ownership_controls) | resource |
| [aws_s3_bucket_policy.lb_logs-policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_policy) | resource |
| [aws_s3_bucket_public_access_block.lb_logs_public_block](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_public_access_block) | resource |
| [aws_s3_bucket_server_side_encryption_configuration.lb_logs-encryption](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_server_side_encryption_configuration) | resource |
| [aws_secretsmanager_secret.secrets](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/secretsmanager_secret) | resource |
| [aws_service_discovery_service.service](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/service_discovery_service) | resource |
| [aws_caller_identity.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/caller_identity) | data source |
| [aws_ecs_cluster.ecs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/ecs_cluster) | data source |
| [aws_elb_service_account.default](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/elb_service_account) | data source |
| [aws_iam_policy_document.assume_role_policy](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_iam_policy_document.ecs_policy_document](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document) | data source |
| [aws_partition.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/partition) | data source |
| [aws_region.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/region) | data source |
| [aws_secretsmanager_secret_version.okta_ipfs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/secretsmanager_secret_version) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_additional_containers"></a> [additional\_containers](#input\_additional\_containers) | n/a | <pre>list(object({<br/>    name   = string<br/>    image  = string<br/>    cpu    = optional(number, null)<br/>    memory = optional(number, null)<br/>    logConfiguration = optional(object({<br/>      logDriver = optional(string, "awslogs")<br/>      options   = optional(map(string), {})<br/>    }), null)<br/>    environment = list(object({<br/>      name  = string<br/>      value = string<br/>    }))<br/>    command = optional(list(string), [])<br/>    secrets = optional(list(object({<br/>      name      = string<br/>      valueFrom = string<br/>    })), [])<br/>    portMappings = optional(list(object({<br/>      containerPort = number<br/>      hostPort      = number<br/>      name          = string<br/>      protocol      = string<br/>    })), [])<br/>    healthCheck = optional(object({<br/>      command     = list(string)<br/>      interval    = number<br/>      retries     = number<br/>      startPeriod = number<br/>      timeout     = number<br/>    }), null)<br/>    dependsOn = optional(list(object({<br/>      containerName = string<br/>      condition     = string<br/>    })))<br/>  }))</pre> | `[]` | no |
| <a name="input_additional_port_mappings"></a> [additional\_port\_mappings](#input\_additional\_port\_mappings) | Additional port mappings for the container | <pre>list(object({<br/>    containerPort = number<br/>    hostPort      = number<br/>    protocol      = string<br/>    appProtocol   = string<br/>  }))</pre> | `[]` | no |
| <a name="input_alb_sg"></a> [alb\_sg](#input\_alb\_sg) | Security groups for the ALB | `list(string)` | `[]` | no |
| <a name="input_app_name"></a> [app\_name](#input\_app\_name) | n/a | `string` | n/a | yes |
| <a name="input_app_subnets"></a> [app\_subnets](#input\_app\_subnets) | A list of subnets to associate with the app. e.g. ['subnet-1a2b3c4d','subnet-1a2b3c4e','subnet-1a2b3c4f'] | `list(string)` | `null` | no |
| <a name="input_assign_public_ip"></a> [assign\_public\_ip](#input\_assign\_public\_ip) | Whether to assign public ip to the service or not - defaults to true for compatibility | `bool` | `true` | no |
| <a name="input_autoscale_cluster_name"></a> [autoscale\_cluster\_name](#input\_autoscale\_cluster\_name) | n/a | `string` | n/a | yes |
| <a name="input_autoscaling_group_name"></a> [autoscaling\_group\_name](#input\_autoscaling\_group\_name) | Autoscaling Group to apply the policy | `string` | `null` | no |
| <a name="input_certificate_arn"></a> [certificate\_arn](#input\_certificate\_arn) | certificate for ALB | `string` | `""` | no |
| <a name="input_cidr"></a> [cidr](#input\_cidr) | n/a | `string` | `""` | no |
| <a name="input_client_keep_alive"></a> [client\_keep\_alive](#input\_client\_keep\_alive) | client\_keep\_alive | `number` | `3600` | no |
| <a name="input_cloudmap_ttl"></a> [cloudmap\_ttl](#input\_cloudmap\_ttl) | n/a | `number` | `300` | no |
| <a name="input_cluster_name"></a> [cluster\_name](#input\_cluster\_name) | n/a | `string` | n/a | yes |
| <a name="input_container_name"></a> [container\_name](#input\_container\_name) | n/a | `string` | `""` | no |
| <a name="input_container_port"></a> [container\_port](#input\_container\_port) | n/a | `string` | `""` | no |
| <a name="input_content_type"></a> [content\_type](#input\_content\_type) | The content type for fixed response | `string` | `"text/plain"` | no |
| <a name="input_cpu"></a> [cpu](#input\_cpu) | n/a | `number` | n/a | yes |
| <a name="input_cpu_statistics"></a> [cpu\_statistics](#input\_cpu\_statistics) | Statistics to use: [Maximum, SampleCount, Sum, Minimum, Average]. Note that resolution used in alarm generated is 1 minute. | `string` | `"Average"` | no |
| <a name="input_cpu_threshold"></a> [cpu\_threshold](#input\_cpu\_threshold) | Keep the ECS Cluster CPU Reservation around this value. Value is in percentage (0..100). Must be specified if cpu based autoscaling is enabled. | `number` | `null` | no |
| <a name="input_create_cert"></a> [create\_cert](#input\_create\_cert) | create cert | `bool` | `false` | no |
| <a name="input_create_secret"></a> [create\_secret](#input\_create\_secret) | create secret | `bool` | `false` | no |
| <a name="input_default_action"></a> [default\_action](#input\_default\_action) | Whether to have a fixed response or a forward behavior as fallback | `string` | `"forward"` | no |
| <a name="input_deployment_maximum_percent"></a> [deployment\_maximum\_percent](#input\_deployment\_maximum\_percent) | n/a | `number` | `200` | no |
| <a name="input_deployment_minimum_healthy_percent"></a> [deployment\_minimum\_healthy\_percent](#input\_deployment\_minimum\_healthy\_percent) | n/a | `number` | `100` | no |
| <a name="input_desired_count"></a> [desired\_count](#input\_desired\_count) | n/a | `number` | `1` | no |
| <a name="input_disable_scale_in"></a> [disable\_scale\_in](#input\_disable\_scale\_in) | Disable scale-in action, defaults to false | `bool` | `false` | no |
| <a name="input_domain_name"></a> [domain\_name](#input\_domain\_name) | domain name | `string` | `"vechain.org"` | no |
| <a name="input_ecr_image_tag"></a> [ecr\_image\_tag](#input\_ecr\_image\_tag) | n/a | `string` | `"latest"` | no |
| <a name="input_ecr_repo_uri"></a> [ecr\_repo\_uri](#input\_ecr\_repo\_uri) | n/a | `string` | `""` | no |
| <a name="input_ecs_sg"></a> [ecs\_sg](#input\_ecs\_sg) | Security groups for the ecs service | `list(string)` | `[]` | no |
| <a name="input_enable_alb"></a> [enable\_alb](#input\_enable\_alb) | If true an ALB is created. | `bool` | `false` | no |
| <a name="input_enable_asg_cpu_based_autoscaling"></a> [enable\_asg\_cpu\_based\_autoscaling](#input\_enable\_asg\_cpu\_based\_autoscaling) | Enable Autoscaling based on ECS Cluster CPU Reservation | `bool` | `false` | no |
| <a name="input_enable_asg_memory_based_autoscaling"></a> [enable\_asg\_memory\_based\_autoscaling](#input\_enable\_asg\_memory\_based\_autoscaling) | Enable Autoscaling based on ECS Cluster Memory Reservation | `bool` | `false` | no |
| <a name="input_enable_deletion_protection"></a> [enable\_deletion\_protection](#input\_enable\_deletion\_protection) | If true, deletion of the load balancer will be disabled. | `bool` | `true` | no |
| <a name="input_enable_dns"></a> [enable\_dns](#input\_enable\_dns) | Enable creation of DNS record. | `bool` | `true` | no |
| <a name="input_enable_ecs_cpu_based_autoscaling"></a> [enable\_ecs\_cpu\_based\_autoscaling](#input\_enable\_ecs\_cpu\_based\_autoscaling) | Enable Autoscaling based on ECS Service CPU Usage | `bool` | `false` | no |
| <a name="input_enable_ecs_memory_based_autoscaling"></a> [enable\_ecs\_memory\_based\_autoscaling](#input\_enable\_ecs\_memory\_based\_autoscaling) | Enable Autoscaling based on ECS Service Memory Usage | `bool` | `false` | no |
| <a name="input_enable_execute_command"></a> [enable\_execute\_command](#input\_enable\_execute\_command) | Enable or disable AWS Exec | `bool` | `true` | no |
| <a name="input_enable_load_balanced"></a> [enable\_load\_balanced](#input\_enable\_load\_balanced) | Enables load balancing for a service by creating a target group and listener rule. This option should NOT be used together with `enable_target_group_connection` delegates the creation of the target group to component that use this module. | `bool` | `false` | no |
| <a name="input_enable_target_group_connection"></a> [enable\_target\_group\_connection](#input\_enable\_target\_group\_connection) | If `true` a load balancer is created for the service which will be connected to the target group specified in `target_group_arn`. Creating a load balancer for an ecs service requires a target group with a connected load balancer. To ensure the right order of creation, provide a list of depended arns in `ecs_services_dependencies` | `bool` | `false` | no |
| <a name="input_enforce_security_group_inbound_rules_on_private_link_traffic"></a> [enforce\_security\_group\_inbound\_rules\_on\_private\_link\_traffic](#input\_enforce\_security\_group\_inbound\_rules\_on\_private\_link\_traffic) | Indicates whether inbound security group rules are enforced for traffic originating from a PrivateLink. Only valid for Load Balancers of type network. The possible values are on and off. | `string` | `null` | no |
| <a name="input_env"></a> [env](#input\_env) | n/a | `string` | `""` | no |
| <a name="input_environment_variables"></a> [environment\_variables](#input\_environment\_variables) | n/a | <pre>list(object({<br/>    name  = string<br/>    value = string<br/>  }))</pre> | n/a | yes |
| <a name="input_extra_permission_actions"></a> [extra\_permission\_actions](#input\_extra\_permission\_actions) | Extra permissions to add to the ECS task execution role | `list(string)` | `[]` | no |
| <a name="input_force_new_deployment"></a> [force\_new\_deployment](#input\_force\_new\_deployment) | force\_new\_deployment | `string` | `false` | no |
| <a name="input_health_check_grace_period_seconds"></a> [health\_check\_grace\_period\_seconds](#input\_health\_check\_grace\_period\_seconds) | The period of time, in seconds, that the ECS service waits before it starts health checks on new tasks | `number` | `null` | no |
| <a name="input_healthcheck"></a> [healthcheck](#input\_healthcheck) | n/a | <pre>object({<br/>    command     = optional(list(string))<br/>    interval    = optional(number)<br/>    retries     = optional(number)<br/>    start_delay = optional(number)<br/>    timeout     = optional(number)<br/>  })</pre> | `null` | no |
| <a name="input_https_tg_healthcheck_interval"></a> [https\_tg\_healthcheck\_interval](#input\_https\_tg\_healthcheck\_interval) | n/a | `number` | `30` | no |
| <a name="input_https_tg_healthcheck_path"></a> [https\_tg\_healthcheck\_path](#input\_https\_tg\_healthcheck\_path) | health check path | `string` | `"/main/v1/healthcheck"` | no |
| <a name="input_https_tg_healthcheck_port"></a> [https\_tg\_healthcheck\_port](#input\_https\_tg\_healthcheck\_port) | n/a | `number` | `null` | no |
| <a name="input_https_tg_healthcheck_timeout"></a> [https\_tg\_healthcheck\_timeout](#input\_https\_tg\_healthcheck\_timeout) | n/a | `number` | `15` | no |
| <a name="input_https_tg_port"></a> [https\_tg\_port](#input\_https\_tg\_port) | n/a | `number` | `8080` | no |
| <a name="input_idle_timeout"></a> [idle\_timeout](#input\_idle\_timeout) | idle\_timeout | `number` | `60` | no |
| <a name="input_internal_alb"></a> [internal\_alb](#input\_internal\_alb) | If true, the load balancer will be internal. | `bool` | `false` | no |
| <a name="input_is_create_repo"></a> [is\_create\_repo](#input\_is\_create\_repo) | n/a | `bool` | `true` | no |
| <a name="input_is_https"></a> [is\_https](#input\_is\_https) | n/a | `bool` | `false` | no |
| <a name="input_is_rule_0_required"></a> [is\_rule\_0\_required](#input\_is\_rule\_0\_required) | is rule 0 required | `bool` | `true` | no |
| <a name="input_is_rule_1_required"></a> [is\_rule\_1\_required](#input\_is\_rule\_1\_required) | is Additional rule 1 required | `bool` | `false` | no |
| <a name="input_is_rule_2_required"></a> [is\_rule\_2\_required](#input\_is\_rule\_2\_required) | is Additional rule 2 required | `bool` | `false` | no |
| <a name="input_is_rule_3_required"></a> [is\_rule\_3\_required](#input\_is\_rule\_3\_required) | is Additional rule 3 required | `bool` | `false` | no |
| <a name="input_is_rule_4_required"></a> [is\_rule\_4\_required](#input\_is\_rule\_4\_required) | is Additional rule 4 required - warning this defaults to true due to backwards compatibility | `bool` | `true` | no |
| <a name="input_is_tg_1_required"></a> [is\_tg\_1\_required](#input\_is\_tg\_1\_required) | is Additional target group 1 required | `bool` | `false` | no |
| <a name="input_is_tg_2_required"></a> [is\_tg\_2\_required](#input\_is\_tg\_2\_required) | is Additional target group 1 required | `bool` | `false` | no |
| <a name="input_kms"></a> [kms](#input\_kms) | n/a | `string` | `""` | no |
| <a name="input_launch_type"></a> [launch\_type](#input\_launch\_type) | n/a | `string` | `"FARGATE"` | no |
| <a name="input_lb_subnets"></a> [lb\_subnets](#input\_lb\_subnets) | A list of subnets to associate with the load balancer. Must be on different AZ e.g. ['subnet-1a2b3c4d','subnet-1a2b3c4e','subnet-1a2b3c4f'] | `list(string)` | `null` | no |
| <a name="input_load_balancer_type"></a> [load\_balancer\_type](#input\_load\_balancer\_type) | Type of load-balancer to be created | `string` | `"application"` | no |
| <a name="input_log_metric_filters"></a> [log\_metric\_filters](#input\_log\_metric\_filters) | Map of metric filters to create | <pre>list(object({<br/>    name    = string<br/>    pattern = string<br/>  }))</pre> | `[]` | no |
| <a name="input_main_cpu"></a> [main\_cpu](#input\_main\_cpu) | n/a | `number` | `null` | no |
| <a name="input_main_memory"></a> [main\_memory](#input\_main\_memory) | n/a | `number` | `null` | no |
| <a name="input_max_capacity"></a> [max\_capacity](#input\_max\_capacity) | Maximum capacity of ECS autoscaling target, cannot be less than min\_capacity | `number` | `null` | no |
| <a name="input_max_size"></a> [max\_size](#input\_max\_size) | n/a | `string` | `""` | no |
| <a name="input_memory"></a> [memory](#input\_memory) | n/a | `number` | n/a | yes |
| <a name="input_memory_statistics"></a> [memory\_statistics](#input\_memory\_statistics) | Statistics to use: [Maximum, SampleCount, Sum, Minimum, Average]. Note that resolution used in alarm generated is 1 minute. | `string` | `"Average"` | no |
| <a name="input_memory_threshold"></a> [memory\_threshold](#input\_memory\_threshold) | Keep the ECS Cluster Memory Reservation around this value. Value is in percentage (0..100). Must be specified if memory based autoscaling is enabled. | `number` | `null` | no |
| <a name="input_message_body"></a> [message\_body](#input\_message\_body) | The message body for fixed response | `string` | `null` | no |
| <a name="input_min_capacity"></a> [min\_capacity](#input\_min\_capacity) | Minimum capacity of ECS autoscaling target, cannot be more than max\_capacity | `number` | `null` | no |
| <a name="input_min_size"></a> [min\_size](#input\_min\_size) | n/a | `string` | `""` | no |
| <a name="input_name"></a> [name](#input\_name) | Name of the ECS Policy created, will appear in Auto Scaling under Service in ECS | `string` | `null` | no |
| <a name="input_namespace_id"></a> [namespace\_id](#input\_namespace\_id) | n/a | `string` | n/a | yes |
| <a name="input_network"></a> [network](#input\_network) | Value for network the service is running on | `string` | `null` | no |
| <a name="input_okta_auth_server_base_url"></a> [okta\_auth\_server\_base\_url](#input\_okta\_auth\_server\_base\_url) | okta endpoint | `string` | `"/"` | no |
| <a name="input_okta_client_id"></a> [okta\_client\_id](#input\_okta\_client\_id) | okta clientid | `string` | `""` | no |
| <a name="input_okta_client_secret"></a> [okta\_client\_secret](#input\_okta\_client\_secret) | okta client secret | `string` | `""` | no |
| <a name="input_private_zone_name"></a> [private\_zone\_name](#input\_private\_zone\_name) | private zone name | `string` | `""` | no |
| <a name="input_private_zone_record_name"></a> [private\_zone\_record\_name](#input\_private\_zone\_record\_name) | Record name for the private Route53 zone | `string` | `""` | no |
| <a name="input_project"></a> [project](#input\_project) | n/a | `string` | `""` | no |
| <a name="input_public_zone_name"></a> [public\_zone\_name](#input\_public\_zone\_name) | public zone name | `string` | `""` | no |
| <a name="input_public_zone_record_name"></a> [public\_zone\_record\_name](#input\_public\_zone\_record\_name) | public zone record name | `string` | `""` | no |
| <a name="input_readonly_root_filesystem"></a> [readonly\_root\_filesystem](#input\_readonly\_root\_filesystem) | Whether containers should have read-only access to the root filesystem | `bool` | `false` | no |
| <a name="input_records"></a> [records](#input\_records) | The records to add to the Route53 subdomain record. | `list(string)` | `[]` | no |
| <a name="input_region"></a> [region](#input\_region) | n/a | `string` | n/a | yes |
| <a name="input_replace_cert"></a> [replace\_cert](#input\_replace\_cert) | n/a | `bool` | `false` | no |
| <a name="input_rule_0_path_pattern"></a> [rule\_0\_path\_pattern](#input\_rule\_0\_path\_pattern) | rule\_1\_path\_pattern ['/api','/api/*'] | `list(string)` | `null` | no |
| <a name="input_rule_1_path_pattern"></a> [rule\_1\_path\_pattern](#input\_rule\_1\_path\_pattern) | rule\_1\_path\_pattern ['/api','/api/*'] | `list(string)` | `null` | no |
| <a name="input_rule_2_path_pattern"></a> [rule\_2\_path\_pattern](#input\_rule\_2\_path\_pattern) | rule\_2\_path\_pattern ['/api','/api/*'] | `list(string)` | `null` | no |
| <a name="input_rule_3_path_pattern"></a> [rule\_3\_path\_pattern](#input\_rule\_3\_path\_pattern) | rule\_3\_path\_pattern ['/api','/api/*'] | `list(string)` | `null` | no |
| <a name="input_runtime_platform"></a> [runtime\_platform](#input\_runtime\_platform) | runtime platform | <pre>list(object({<br/>    operating_system_family = string<br/>    cpu_architecture        = string<br/>  }))</pre> | <pre>[<br/>  {<br/>    "cpu_architecture": "ARM64",<br/>    "operating_system_family": "LINUX"<br/>  }<br/>]</pre> | no |
| <a name="input_scale_in_cooldown"></a> [scale\_in\_cooldown](#input\_scale\_in\_cooldown) | Time between scale in action | `number` | `300` | no |
| <a name="input_scale_out_cooldown"></a> [scale\_out\_cooldown](#input\_scale\_out\_cooldown) | Time between scale out action | `number` | `300` | no |
| <a name="input_scan_all_repo_images"></a> [scan\_all\_repo\_images](#input\_scan\_all\_repo\_images) | Scan all pushed images to each repository in ECR | `bool` | `true` | no |
| <a name="input_secret_id"></a> [secret\_id](#input\_secret\_id) | secret id | `string` | `""` | no |
| <a name="input_secrets_enable"></a> [secrets\_enable](#input\_secrets\_enable) | n/a | `bool` | `false` | no |
| <a name="input_security_groups"></a> [security\_groups](#input\_security\_groups) | The security groups to attach to the load balancer. e.g. ["sg-edcd9784","sg-edcd9785"] | `list(string)` | `[]` | no |
| <a name="input_sensitive_environment_variables"></a> [sensitive\_environment\_variables](#input\_sensitive\_environment\_variables) | n/a | <pre>list(object({<br/>    name      = string<br/>    valueFrom = string<br/>  }))</pre> | `[]` | no |
| <a name="input_service_discovery_name"></a> [service\_discovery\_name](#input\_service\_discovery\_name) | n/a | `string` | `""` | no |
| <a name="input_ssl_policy"></a> [ssl\_policy](#input\_ssl\_policy) | The name of the SSL Policy for the listener | `string` | `"ELBSecurityPolicy-2016-08"` | no |
| <a name="input_status_code"></a> [status\_code](#input\_status\_code) | The status code for fixed response | `string` | `"403"` | no |
| <a name="input_subdomain_type"></a> [subdomain\_type](#input\_subdomain\_type) | The type of Route53 subdomain record. | `string` | `"A"` | no |
| <a name="input_target_cpu_value"></a> [target\_cpu\_value](#input\_target\_cpu\_value) | Autoscale when CPU Usage value over the specified value. Must be specified if `enable_cpu_based_autoscaling` is `true`. | `number` | `null` | no |
| <a name="input_target_memory_value"></a> [target\_memory\_value](#input\_target\_memory\_value) | Autoscale when Memory Usage value over the specified value. Must be specified if `enable_memory_based_autoscaling` is `true`. | `number` | `null` | no |
| <a name="input_tg_1_healthcheck_path"></a> [tg\_1\_healthcheck\_path](#input\_tg\_1\_healthcheck\_path) | health check path | `string` | `"/"` | no |
| <a name="input_tg_1_healthcheck_port"></a> [tg\_1\_healthcheck\_port](#input\_tg\_1\_healthcheck\_port) | n/a | `number` | `8080` | no |
| <a name="input_tg_1_name"></a> [tg\_1\_name](#input\_tg\_1\_name) | tg\_1\_name | `string` | `"tg_1"` | no |
| <a name="input_tg_1_port"></a> [tg\_1\_port](#input\_tg\_1\_port) | n/a | `number` | `8080` | no |
| <a name="input_tg_2_healthcheck_path"></a> [tg\_2\_healthcheck\_path](#input\_tg\_2\_healthcheck\_path) | health check path | `string` | `"/"` | no |
| <a name="input_tg_2_healthcheck_port"></a> [tg\_2\_healthcheck\_port](#input\_tg\_2\_healthcheck\_port) | n/a | `number` | `8080` | no |
| <a name="input_tg_2_name"></a> [tg\_2\_name](#input\_tg\_2\_name) | tg\_2\_name | `string` | `"tg_2"` | no |
| <a name="input_tg_2_port"></a> [tg\_2\_port](#input\_tg\_2\_port) | n/a | `number` | `8080` | no |
| <a name="input_ttl"></a> [ttl](#input\_ttl) | The TTL (Time-to-live) value for the Route53 record. | `number` | `300` | no |
| <a name="input_vpc_id"></a> [vpc\_id](#input\_vpc\_id) | VPC id where the load balancer and other resources will be deployed. | `string` | `null` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_alb_arn"></a> [alb\_arn](#output\_alb\_arn) | The ARN of the ALB |
| <a name="output_alb_dns_name"></a> [alb\_dns\_name](#output\_alb\_dns\_name) | The DNS name of the ALB |
| <a name="output_alb_http_listener_arn"></a> [alb\_http\_listener\_arn](#output\_alb\_http\_listener\_arn) | The ARN of the ALB HTTP listener |
| <a name="output_alb_https_listener_arn"></a> [alb\_https\_listener\_arn](#output\_alb\_https\_listener\_arn) | The ARN of the ALB HTTPS listener |
| <a name="output_alb_tg"></a> [alb\_tg](#output\_alb\_tg) | The ARN of the ALB Target Group |
| <a name="output_alb_zone_id"></a> [alb\_zone\_id](#output\_alb\_zone\_id) | Zone id for alb |
| <a name="output_cluster_name"></a> [cluster\_name](#output\_cluster\_name) | The ECS cluster name |
| <a name="output_ecr_repository_arn"></a> [ecr\_repository\_arn](#output\_ecr\_repository\_arn) | The ARN of the ECR repository |
| <a name="output_ecr_repository_url"></a> [ecr\_repository\_url](#output\_ecr\_repository\_url) | The URL of the ECR repository |
| <a name="output_ecs_cloudwatch_log_group_name"></a> [ecs\_cloudwatch\_log\_group\_name](#output\_ecs\_cloudwatch\_log\_group\_name) | The name of the CloudWatch log group for the ECS service |
| <a name="output_ecs_task_execution_role_id"></a> [ecs\_task\_execution\_role\_id](#output\_ecs\_task\_execution\_role\_id) | The id of the ECS execution task role, so it can be customised to allow additional fine-grained privileges |
| <a name="output_log_metric_names"></a> [log\_metric\_names](#output\_log\_metric\_names) | The names of the metric filters |
| <a name="output_name"></a> [name](#output\_name) | The name of the ALB |
| <a name="output_nlb_tg"></a> [nlb\_tg](#output\_nlb\_tg) | The ARN of the NLB Target Group |
| <a name="output_secrets_name"></a> [secrets\_name](#output\_secrets\_name) | The name of the Secrets Manager secret. |
| <a name="output_service_name"></a> [service\_name](#output\_service\_name) | The ECS service name |
| <a name="output_tg1_arn"></a> [tg1\_arn](#output\_tg1\_arn) | n/a |
| <a name="output_tg2_arn"></a> [tg2\_arn](#output\_tg2\_arn) | n/a |
<!-- END_TF_DOCS -->

## How to use
```hcl
module "ecs-backend-service-chain-scanner" {
  source           = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecs-loadbalanced-webservice?ref=v.1.0.9"
  vpc_id                     = var.vpc_id
  lb_subnets                 = var.public_subnets
  app_subnets                = var.private_subnets
  env                        = var.env
  app_name                   = var.app_name
  ecr_image_tag              = var.ecr_image_tag
  project                    = var.project
  is_create_repo             = false
  image_repo_url             = var.image_repo_url
  secrets_enable             = false
  assign_public_ip           = false
  cpu                        = var.cpu
  memory                     = var.memory
  cidr                       = var.cidr
  desired_count              = var.desired_count
  container_port             = 8080
  host_port                  = 8080
  certificate_arn            = var.certificate_arn
  rule_0_path_pattern        = ["/api/v*", "/api-docs", "/swagger-ui/*"]
  ecs_sg                     = var.ecs_sg
  alb_sg                     = var.alb_sg
  namespace_id               = aws_service_discovery_private_dns_namespace.ns.id
  enable_deletion_protection = true
  ssl_policy                 = "ELBSecurityPolicy-TLS-1-2-2017-01"
  https_tg_healthcheck_path  = "/actuator/health"
    log_metric_filters = [
    {
      name    = "AppUnhealthy",
      pattern = "Application is UNHEALTHY"
    }
  ]

  environment_variables = [
  ]
  runtime_platform = var.runtime_platform
  
  # Enhanced security - read-only root filesystem
  readonly_root_filesystem = true
}

  ####### enable autoscailing #######
  enable_ecs_cpu_based_autoscaling = true
  enable_ecs_memory_based_autoscaling = true
  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
  target_cpu_value = 70
  target_memory_value = 70
  disable_scale_in = false
  # scale_in_cooldown = 300
  # scale_out_cooldown = 300
  name = "auto-scaling-group"
