variable "env" {
  description = "The environment in which the infrastructure is deployed"
  type        = string
}

variable "project" {
  description = "The name of the project"
  default     = ""
}


variable "vpc_id" {
  default = ""
}

variable "cidr" {
  default = ""
}

variable "enable_container_insights" {
  description = "Enable container insights for the ECS cluster"
  type        = bool
  default     = true
}

variable "app_name" {
  description = "The name of the application"
  type        = string
  default     = "app"
}

variable "is_create_repo" {
  description = "Whether to create ECR repository"
  type        = bool
  default     = false
}

variable "scan_all_repo_images" {
  description = "Scan all pushed images to each repository in ECR"
  type        = bool
  default     = true
}
