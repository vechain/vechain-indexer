variable "enable" {
  description = "Enable ECR"
  type        = bool
  default     = true
}

variable "enable_private_ecr" {
  description = "Enable private ECR"
  type        = bool
  default     = true
}

variable "env" {
  description = "The environment for the ECR registry"
  type        = string
  default     = null
}

variable "project" {
  description = "The name of the project"
  type        = string
  default     = "Project"
}

variable "app_name" {
  description = "The name of the application"
  type        = string
  default     = "app_name"
}

variable "image_tag_mutability" {
  description = "Provide image mutability"
  type        = string
  default     = "IMMUTABLE"
}

variable "encryption_type" {
  description = "Provide type of encryption here"
  type        = string
  default     = "KMS"
}

variable "max_untagged_image_count" {
  description = "Provide the maximum number of untagged images"
  type        = number
  default     = 1
}

variable "max_image_count" {
  description = "Provide the maximum number of images"
  type        = number
  default     = 10
}

variable "principals_readonly_access" {
  type        = list(any)
  default     = []
  description = "Principal ARN to provide with readonly access to the ECR."
}

variable "principals_full_access" {
  type        = list(any)
  description = "Principal ARN to provide with full access to the ECR."
  default     = []
}

variable "scan_on_push" {
  type        = bool
  description = "Indicates whether images are scanned after being pushed to the repository (true) or not scanned (false)."
  default     = true
}

variable "enable_scan_configuration" {
  description = "Enable ECR registry scanning configuration"
  type        = bool
  default     = false
}

variable "scan_type" {
  description = "Type of scan to run on container images when enable_scan_configuration is true. Valid values are BASIC and ENHANCED"
  type        = string
  default     = "BASIC"

  validation {
    condition     = contains(["BASIC", "ENHANCED"], var.scan_type)
    error_message = "scan_type must be either BASIC or ENHANCED"
  }
}

variable "scan_frequency" {
  description = "Frequency of scanning container images when enable_scan_configuration is true. Valid values are SCAN_ON_PUSH, CONTINUOUS_SCAN and MANUAL"
  type        = string
  default     = "SCAN_ON_PUSH"

  validation {
    condition     = contains(["SCAN_ON_PUSH", "CONTINUOUS_SCAN", "MANUAL"], var.scan_frequency)
    error_message = "scan_frequency must be either SCAN_ON_PUSH, CONTINUOUS_SCAN or MANUAL"
  }
}

variable "scan_filter_pattern" {
  description = "Pattern for filtering repositories when enable_scan_configuration is true. Use * for wildcard matching"
  type        = string
  default     = "*"
}

variable "scan_filter_type" {
  description = "Type of filter pattern when enable_scan_configuration is true. Valid values are WILDCARD and PREFIX_MATCH"
  type        = string
  default     = "WILDCARD"

  validation {
    condition     = contains(["WILDCARD", "PREFIX_MATCH"], var.scan_filter_type)
    error_message = "filter_type must be either WILDCARD or PREFIX_MATCH"
  }
}

