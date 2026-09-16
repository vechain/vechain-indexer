variable "create_cloudwatch_dashboard" {
  description = "Create CloudWatch dashboard"
  type        = bool
  default     = false
}

variable "dashboard_name" {
  description = "Name of the CloudWatch dashboard"
  type        = string
  default     = null
}

variable "dashboard_body" {
  description = "Body of the CloudWatch dashboard"
  type        = string
  default     = null
}
