variable "service_name" {
  description = "Value of the `service` external label on emitted metrics. Typically `indexer` or `api`."
  type        = string
}

variable "env" {
  description = "Value of the `env` external label. Typically `prod`."
  type        = string
}

variable "deployment" {
  description = "Value of the `deployment` external label — `blue` or `green`."
  type        = string
  validation {
    condition     = contains(["blue", "green"], var.deployment)
    error_message = "deployment must be blue or green."
  }
}

variable "network" {
  description = "Value of the `network` external label — `mainnet` or `testnet`."
  type        = string
  validation {
    condition     = contains(["mainnet", "testnet"], var.network)
    error_message = "network must be mainnet or testnet."
  }
}

variable "aws_region" {
  description = "AWS region for SigV4 signing to AMP."
  type        = string
}

variable "amp_endpoint" {
  description = "AMP workspace endpoint (base URL, trailing slash required). Sidecar remote-writes to <endpoint>api/v1/remote_write."
  type        = string
  validation {
    condition     = endswith(var.amp_endpoint, "/")
    error_message = "amp_endpoint must end with a trailing slash."
  }
}

variable "amp_workspace_arn" {
  description = "AMP workspace ARN — Resource on the aps:RemoteWrite statement."
  type        = string
}

variable "log_group_name" {
  description = "CloudWatch log group the sidecar writes stderr to."
  type        = string
}

variable "app_port" {
  description = "Port the app container exposes /actuator/prometheus on. Sidecar scrapes localhost:<app_port>/actuator/prometheus."
  type        = number
  default     = 8080
}

variable "adot_image_tag" {
  description = "aws-otel-collector image tag pin."
  type        = string
  default     = "v0.46.0"
}

variable "memory_limit_mib" {
  description = "Hard memory limit (MiB) for the sidecar container."
  type        = number
  default     = 256
}
