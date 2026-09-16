variable "subnets" {
  description = "A list of subnets for the ecs service."
  type        = list(string)
  default     = null
}

variable "security_groups" {
  description = "The security groups to attach to the load balancer. e.g. [\"sg-edcd9784\",\"sg-edcd9785\"]"
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

variable "command" {
  description = "The command arguments that are passed to the container entrypoint"
  type        = list(string)
  default     = []
}

variable "cidr" {
  default = ""
}

variable "desired_capacity" {
  type    = number
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

variable "kms_key_id" {
  default = ""
}
variable "kms_key_arn" {
  default = "*"
}

variable "cluster" {
  default = ""
}

variable "hostPort" {
  type = number
}

variable "secrets_enable" {
  default = false
  type    = bool
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

variable "launch_type" {
  type    = string
  default = "FARGATE"

  validation {
    condition     = contains(["FARGATE", "EC2", "omit"], var.launch_type)
    error_message = "Invalid input, options: FARGATE, EC2 or omit for null."
  }
}

variable "containerPort" {
  type = number
}

variable "execution_role_arn" {
  default = ""
}
variable "environment_variables" {
  type = list(object({
    name  = string
    value = string
  }))
}

variable "sensitive_environment_variables" {
  type = list(object({
    name      = string
    valueFrom = string
  }))
  default = []
}

variable "additional_secrets" {
  description = "A map of additional secret names to their ARN values"
  type        = map(string)
  default     = {}
}

variable "extra_statements" {
  description = "Additional IAM policy statements"
  type = list(object({
    sid       = string
    effect    = string
    actions   = list(string)
    resources = list(string)
  }))
  default = null # This makes the variable optional
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

variable "enable_execute_command" {
  type    = bool
  default = true
}

variable "deployment_minimum_healthy_percent" {
  type    = number
  default = 100
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

variable "health_check_grace_period_seconds" {
  description = "Seconds to ignore failing health checks after a task starts. Requires a service registry or load balancer."
  type        = number
  default     = 0
}

variable "is_create_repo" {
  type    = bool
  default = true
}

variable "log_metric_filters" {
  description = "Map of metric filters to create"
  type = list(object({
    name    = string
    pattern = string
  }))
  default = []
}


variable "ecr_image_tag" {
  type    = string
  default = ""
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
    logConfiguration = optional(object({
      logDriver = optional(string, "awslogs")
      options   = optional(map(string), {})
    }), null)
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

variable "cloudmap_ttl" {
  type    = number
  default = 300 #backwards compatible
}

variable "network" {
  description = "Value for network the service is running on"
  type        = string
  default     = null
}

variable "scan_all_repo_images" {
  type        = bool
  description = "Scan all pushed images to each repository in ECR"
  default     = true
}

variable "service_discovery_name" {
  type    = string
  default = ""
}

variable "readonly_root_filesystem" {
  description = "Whether containers should have read-only access to the root filesystem"
  type        = bool
  default     = false
}
