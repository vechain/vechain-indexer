variable "simple_alarms" {
  description = "Map of alarms to create"
  type = map(object({
    alarm_name          = string
    comparison_operator = string
    evaluation_periods  = number
    metric_name         = string
    namespace           = string
    period              = number
    statistic           = string
    threshold           = number
    alarm_description   = string
    dimensions          = optional(map(string))
  }))
}


variable "expression_alarms" {
  description = "Map of alarms to create"
  type = map(object({
    alarm_name          = string
    comparison_operator = string
    evaluation_periods  = number
    metric_name         = string
    namespace           = string
    period              = number
    statistic           = string
    threshold           = number
    alarm_description   = string
    clustername         = optional(string)
    servicename         = optional(string)

    metric_query = optional(list(object({
      id          = string
      expression  = optional(string)
      label       = optional(string)
      return_data = optional(bool)
      metric = optional(list(object({
        dimensions  = optional(map(string))
        metric_name = string
        namespace   = string
        period      = number
        stat        = string
        unit        = optional(string)
      })))
    })))
  }))
}
variable "create_slack_integration" {
  description = "Slack integration configuration"
  type        = bool
  default     = false
}
variable "configuration_name" {
  description = "Name of the configuration"
  type        = string
  default     = null
}

variable "slack_channel_id" {
  description = "ID of the Slack channel"
  type        = string
  default     = null
}

variable "slack_workspace_id" {
  description = "ID of the Slack workspace"
  type        = string
  default     = ""
}

variable "sns_topic_enabled" {
  description = "ARNs of the SNS topics"
  type        = string
}

variable "topic_name" {
  description = "Name of the SNS topic"
  type        = string
}

variable "email_subscriptions" {
  description = "List of email addresses to subscribe to the SNS topic"
  type        = list(string)
}
