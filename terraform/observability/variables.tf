variable "project" {
  description = "Name prefix for AMP / AMG resources. Unique within the AWS account."
  type        = string
  default     = "veworld-indexer"
}

variable "slack_webhook_url" {
  description = "Slack incoming-webhook URL for AMP alert delivery. Empty string writes a `placeholder` sentinel to Secrets Manager and the bridge Lambda no-ops."
  type        = string
  default     = ""
  sensitive   = true
}
