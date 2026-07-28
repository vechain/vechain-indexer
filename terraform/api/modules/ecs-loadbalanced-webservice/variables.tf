variable "app_subnets" {
  description = "A list of subnets to associate with the app. e.g. ['subnet-1a2b3c4d','subnet-1a2b3c4e','subnet-1a2b3c4f']"
  type        = list(string)
  default     = null
}

variable "enable_deletion_protection" {
  description = "If true, deletion of the load balancer will be disabled."
  type        = bool
  default     = true
}

variable "enforce_security_group_inbound_rules_on_private_link_traffic" {
  description = "Indicates whether inbound security group rules are enforced for traffic originating from a PrivateLink. Only valid for Load Balancers of type network. The possible values are on and off."
  type        = string
  default     = null
}

variable "lb_subnets" {
  description = "A list of subnets to associate with the load balancer. Must be on different AZ e.g. ['subnet-1a2b3c4d','subnet-1a2b3c4e','subnet-1a2b3c4f']"
  type        = list(string)
  default     = null
}

variable "internal_alb" {
  description = "If true, the load balancer will be internal."
  type        = bool
  default     = false
}

variable "security_groups" {
  description = "The security groups to attach to the load balancer. e.g. [\"sg-edcd9784\",\"sg-edcd9785\"]"
  type        = list(string)
  default     = []
}

variable "service_discovery_name" {
  type    = string
  default = ""
}

variable "alb_sg" {
  description = "Security groups for the ALB"
  type        = list(string)
  default     = []
}

variable "ecs_sg" {
  description = "Security groups for the ecs service"
  type        = list(string)
  default     = []
}

variable "vpc_id" {
  description = "VPC id where the load balancer and other resources will be deployed."
  type        = string
  default     = null
}

variable "namespace_id" {
  type = string
}

variable "env" {
  default = ""
}

variable "app_name" {
  type = string
}

variable "project" {
  default = ""
}

variable "region" {
  type = string
}


variable "cpu" {
  type = number
}

variable "memory" {
  type = number
}


variable "cidr" {
  default = ""
}

variable "desired_count" {
  default = 1
}

variable "max_size" {
  default = ""
}

variable "min_size" {
  default = ""
}

variable "container_name" {
  default = ""
}

variable "container_port" {
  default = ""
}

variable "secrets_enable" {
  default = false
  type    = bool
}

variable "sensitive_environment_variables" {
  type = list(object({
    name      = string
    valueFrom = string
  }))
  default = []
}

variable "kms" {
  default = ""
}

variable "runtime_platform" {
  description = "runtime platform"
  type = list(object({
    operating_system_family = string
    cpu_architecture        = string
  }))
  default = [{
    operating_system_family = "LINUX",
    cpu_architecture        = "ARM64"
  }]
}

variable "enable_alb" {
  description = "If true an ALB is created."
  type        = bool
  default     = false
}

variable "enable_dns" {
  description = "Enable creation of DNS record."
  type        = bool
  default     = true
}

variable "enable_target_group_connection" {
  description = "If `true` a load balancer is created for the service which will be connected to the target group specified in `target_group_arn`. Creating a load balancer for an ecs service requires a target group with a connected load balancer. To ensure the right order of creation, provide a list of depended arns in `ecs_services_dependencies`"
  type        = bool
  default     = false
}

variable "enable_load_balanced" {
  description = "Enables load balancing for a service by creating a target group and listener rule. This option should NOT be used together with `enable_target_group_connection` delegates the creation of the target group to component that use this module."
  type        = bool
  default     = false
}

variable "load_balancer_type" {
  description = "Type of load-balancer to be created"
  type        = string
  default     = "application"
}

variable "ssl_policy" {
  description = "The name of the SSL Policy for the listener"
  type        = string
  default     = "ELBSecurityPolicy-2016-08"
}

variable "enable_execute_command" {
  description = "Enable or disable AWS Exec"
  type        = bool
  default     = true
}

variable "certificate_arn" {
  description = "certificate for ALB"
  type        = string
  default     = ""
}

variable "environment_variables" {
  type = list(object({
    name  = string
    value = string
  }))
}

variable "https_tg_healthcheck_interval" {
  type    = number
  default = 30
}

variable "https_tg_healthcheck_port" {
  type    = number
  default = null
}

variable "https_tg_healthcheck_timeout" {
  type    = number
  default = 15
}

variable "https_tg_port" {
  type    = number
  default = 8080
}
variable "https_tg_healthcheck_path" {
  description = "health check path"
  type        = string
  default     = "/main/v1/healthcheck"
}


variable "is_tg_1_required" {
  description = "is Additional target group 1 required"
  type        = bool
  default     = false
}

variable "tg_1_name" {
  description = "tg_1_name"
  type        = string
  default     = "tg_1"
}

variable "tg_1_port" {
  type    = number
  default = 8080
}


variable "tg_1_healthcheck_port" {
  type    = number
  default = 8080
}

variable "tg_1_healthcheck_path" {
  description = "health check path"
  type        = string
  default     = "/"
}

variable "is_tg_2_required" {
  description = "is Additional target group 1 required"
  type        = bool
  default     = false
}

variable "tg_2_name" {
  description = "tg_2_name"
  type        = string
  default     = "tg_2"
}

variable "tg_2_port" {
  type    = number
  default = 8080
}


variable "tg_2_healthcheck_port" {
  type    = number
  default = 8080
}

variable "tg_2_healthcheck_path" {
  description = "health check path"
  type        = string
  default     = "/"
}

variable "is_rule_0_required" {
  description = "is rule 0 required"
  type        = bool
  default     = true
}

variable "is_rule_1_required" {
  description = "is Additional rule 1 required"
  type        = bool
  default     = false
}

variable "is_rule_2_required" {
  description = "is Additional rule 2 required"
  type        = bool
  default     = false
}

variable "is_rule_3_required" {
  description = "is Additional rule 3 required"
  type        = bool
  default     = false
}

variable "is_rule_4_required" {
  description = "is Additional rule 4 required - warning this defaults to true due to backwards compatibility"
  type        = bool
  default     = true
}

variable "rule_0_path_pattern" {
  description = "rule_1_path_pattern ['/api','/api/*']"
  type        = list(string)
  default     = null
}
variable "rule_1_path_pattern" {
  description = "rule_1_path_pattern ['/api','/api/*']"
  type        = list(string)
  default     = null
}

variable "rule_2_path_pattern" {
  description = "rule_2_path_pattern ['/api','/api/*']"
  type        = list(string)
  default     = null
}

variable "rule_3_path_pattern" {
  description = "rule_3_path_pattern ['/api','/api/*']"
  type        = list(string)
  default     = null
}

variable "idle_timeout" {
  description = "idle_timeout"
  type        = number
  default     = 60
}

variable "client_keep_alive" {
  description = "client_keep_alive"
  type        = number
  default     = 3600
}

variable "okta_auth_server_base_url" {
  description = "okta endpoint"
  type        = string
  default     = "/"
}

variable "okta_client_id" {
  description = "okta clientid"
  type        = string
  default     = ""
}

variable "okta_client_secret" {
  description = "okta client secret"
  type        = string
  default     = ""
}

variable "secret_id" {
  description = "secret id"
  type        = string
  default     = ""

}

variable "public_zone_name" {
  description = "public zone name"
  type        = string
  default     = ""
}

variable "public_zone_record_name" {
  description = "public zone record name"
  type        = string
  default     = ""

}

variable "domain_name" {
  description = "domain name"
  type        = string
  default     = "vechain.org"
}

variable "create_cert" {
  description = "create cert"
  type        = bool
  default     = false
}

variable "private_zone_name" {
  description = "private zone name"
  type        = string
  default     = ""
}

variable "private_zone_record_name" {
  description = "Record name for the private Route53 zone"
  type        = string
  default     = ""
}

variable "subdomain_type" {
  description = "The type of Route53 subdomain record."
  type        = string
  default     = "A" # default value set to "A"
}

variable "ttl" {
  description = "The TTL (Time-to-live) value for the Route53 record."
  type        = number
  default     = 300 # default value set to 300
}

variable "records" {
  description = "The records to add to the Route53 subdomain record."
  type        = list(string)
  default     = []

}

variable "create_secret" {
  description = "create secret"
  type        = bool
  default     = false
}


variable "force_new_deployment" {
  description = "force_new_deployment"
  type        = string
  default     = false
}

variable "deployment_maximum_percent" {
  type    = number
  default = 200

}


variable "deployment_minimum_healthy_percent" {
  type    = number
  default = 100
}

variable "is_create_repo" {
  type    = bool
  default = true
}

################################################################################
# App autoscaling policy
################################################################################
variable "enable_ecs_cpu_based_autoscaling" {
  description = "Enable Autoscaling based on ECS Service CPU Usage"
  type        = bool
  default     = false
}

variable "target_cpu_value" {
  description = "Autoscale when CPU Usage value over the specified value. Must be specified if `enable_cpu_based_autoscaling` is `true`."
  type        = number
  default     = null
}

variable "disable_scale_in" {
  description = "Disable scale-in action, defaults to false"
  type        = bool
  default     = false
}

variable "scale_in_cooldown" {
  description = "Time between scale in action"
  type        = number
  default     = 300
}

variable "scale_out_cooldown" {
  description = "Time between scale out action"
  type        = number
  default     = 300
}

variable "enable_ecs_memory_based_autoscaling" {
  description = "Enable Autoscaling based on ECS Service Memory Usage"
  type        = bool
  default     = false
}

variable "target_memory_value" {
  description = "Autoscale when Memory Usage value over the specified value. Must be specified if `enable_memory_based_autoscaling` is `true`."
  type        = number
  default     = null
}

variable "min_capacity" {
  description = "Minimum capacity of ECS autoscaling target, cannot be more than max_capacity"
  type        = number
  default     = null
}

variable "max_capacity" {
  description = "Maximum capacity of ECS autoscaling target, cannot be less than min_capacity"
  type        = number
  default     = null
}

variable "name" {
  description = "Name of the ECS Policy created, will appear in Auto Scaling under Service in ECS"
  type        = string
  default     = null
}

variable "health_check_grace_period_seconds" {
  description = "The period of time, in seconds, that the ECS service waits before it starts health checks on new tasks"
  type        = number
  default     = null
}

variable "extra_permission_actions" {
  description = "Extra permissions to add to the ECS task execution role"
  type        = list(string)
  default     = []
}

################################################################################
# Autoscaling policy
################################################################################
variable "enable_asg_cpu_based_autoscaling" {
  description = "Enable Autoscaling based on ECS Cluster CPU Reservation"
  type        = bool
  default     = false
}

variable "autoscaling_group_name" {
  description = "Autoscaling Group to apply the policy"
  type        = string
  default     = null
}

variable "cpu_threshold" {
  description = "Keep the ECS Cluster CPU Reservation around this value. Value is in percentage (0..100). Must be specified if cpu based autoscaling is enabled."
  type        = number
  default     = null
}


variable "enable_asg_memory_based_autoscaling" {
  description = "Enable Autoscaling based on ECS Cluster Memory Reservation"
  type        = bool
  default     = false
}


variable "memory_threshold" {
  description = "Keep the ECS Cluster Memory Reservation around this value. Value is in percentage (0..100). Must be specified if memory based autoscaling is enabled."
  type        = number
  default     = null
}

variable "cpu_statistics" {
  description = "Statistics to use: [Maximum, SampleCount, Sum, Minimum, Average]. Note that resolution used in alarm generated is 1 minute."
  type        = string
  default     = "Average"
}

variable "memory_statistics" {
  description = "Statistics to use: [Maximum, SampleCount, Sum, Minimum, Average]. Note that resolution used in alarm generated is 1 minute."
  type        = string
  default     = "Average"
}

variable "cluster_name" {
  type = string
}

variable "default_action" {
  type        = string
  description = "Whether to have a fixed response or a forward behavior as fallback"
  default     = "forward"
}

variable "content_type" {
  type        = string
  description = "The content type for fixed response"
  default     = "text/plain"
}

variable "message_body" {
  type        = string
  description = "The message body for fixed response"
  default     = null
}

variable "status_code" {
  type        = string
  description = "The status code for fixed response"
  default     = "403"
}

variable "log_metric_filters" {
  description = "Map of metric filters to create"
  type = list(object({
    name    = string
    pattern = string
  }))
  default = []
}

variable "assign_public_ip" {
  type        = bool
  description = "Whether to assign public ip to the service or not - defaults to true for compatibility"
  default     = true
}

variable "replace_cert" {
  type    = bool
  default = false
}

variable "autoscale_cluster_name" {
  type = string
}

variable "ecr_image_tag" {
  type    = string
  default = "latest"
}

variable "ecr_repo_uri" {
  type    = string
  default = ""
}

variable "additional_containers" {
  type = list(object({
    name   = string
    image  = string
    cpu    = optional(number, null)
    memory = optional(number, null)
    logConfiguration = optional(object({
      logDriver = optional(string, "awslogs")
      options   = optional(map(string), {})
    }), null)
    environment = list(object({
      name  = string
      value = string
    }))
    command = optional(list(string), [])
    secrets = optional(list(object({
      name      = string
      valueFrom = string
    })), [])
    portMappings = optional(list(object({
      containerPort = number
      hostPort      = number
      name          = string
      protocol      = string
    })), [])
    healthCheck = optional(object({
      command     = list(string)
      interval    = number
      retries     = number
      startPeriod = number
      timeout     = number
    }), null)
    dependsOn = optional(list(object({
      containerName = string
      condition     = string
    })))
  }))
  default = []
}

variable "additional_port_mappings" {
  description = "Additional port mappings for the container"
  type = list(object({
    containerPort = number
    hostPort      = number
    protocol      = string
    appProtocol   = string
  }))
  default = []
}

variable "main_cpu" {
  type    = number
  default = null
}

variable "main_memory" {
  type    = number
  default = null
}

variable "healthcheck" {
  type = object({
    command     = optional(list(string))
    interval    = optional(number)
    retries     = optional(number)
    start_delay = optional(number)
    timeout     = optional(number)
  })
  default = null
}

variable "cloudmap_ttl" {
  type    = number
  default = 300 #backwards compatible

}

variable "launch_type" {
  type    = string
  default = "FARGATE"

  validation {
    condition     = contains(["FARGATE", "EC2", "omit"], var.launch_type)
    error_message = "Invalid input, options: FARGATE, EC2 or omit for null."
  }
}

variable "scan_all_repo_images" {
  type        = bool
  description = "Scan all pushed images to each repository in ECR"
  default     = true
}

variable "network" {
  description = "Value for network the service is running on"
  type        = string
  default     = null
}

variable "is_https" {
  type    = bool
  default = false
}

variable "readonly_root_filesystem" {
  description = "Whether containers should have read-only access to the root filesystem"
  type        = bool
  default     = false
}
