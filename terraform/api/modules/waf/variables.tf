variable "env" {
  type        = string
  description = "Deployment environment"
}

variable "project_name" {
  type        = string
  description = "Project name"
}

variable "waf_cloudfront_enable" {
  type        = bool
  description = "Enable WAF for Cloudfront distribution"
  default     = false
}

variable "waf_regional_enable" {
  type        = bool
  description = "Enable WAFv2 to ALB, API Gateway or AppSync GraphQL API"
  default     = false
}

variable "logs_enable" {
  type        = bool
  description = "Enable logs"
  default     = false
}

variable "logs_s3_enable" {
  type        = bool
  description = "Enable logs to destination s3"
  default     = false
}

variable "logs_retension" {
  type        = number
  description = "Specifies the number of days you want to retain log events in the specified log group. Possible values are: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653, and 0. If you select 0, the events in the log group are always retained and never expire."
  default     = 90
}

variable "logging_redacted_fields" {
  type = list(object({
    all_query_arguments   = string
    body                  = string
    method                = string
    query_string          = string
    single_header         = string
    single_query_argument = string
    uri_path              = string
  }))
  description = "Redacted fields"
  default     = []
}

variable "logging_filter" {
  type = list(object({
    default_behavior = string
    filter = list(object({
      behavior    = string
      requirement = string
      condition = list(object({
        action_condition     = string
        label_name_condition = string
      }))
    }))
  }))
  description = "Filter"
  default     = []
}

variable "global_rule" {
  description = "Cloudfront WAF Rule Name"
  type        = string
  default     = ""
}

variable "regional_rule" {
  description = "Regional WAF Rules for ALB and API Gateway"
  type        = string
  default     = ""
}

variable "default_action" {
  type    = string
  default = "block"
}

variable "scope" {
  type        = string
  description = "The scope of this Web ACL. Valid options: CLOUDFRONT, REGIONAL(ALB)."
}

########## Associate WAFv2 Rules to CloudFront, ALB or API Gateway

variable "web_acl_id" {
  description = "Specify a web ACL ARN to be associated in CloudFront Distribution / # Optional WEB ACLs (WAF) to attach to CloudFront"
  type        = string
  default     = null
}

variable "associate_waf" {
  type        = bool
  description = "Whether to associate resources (ALBs, API Gateways, etc.) with the WAFv2 ACL. Set to true and provide resource ARNs via resource_arn or associated_alb_arns."
  default     = false
}

variable "resource_arn" {
  type        = list(string)
  description = "List of resource ARNs (ALBs, API Gateways, AppSync, etc.) to associate with the WAFv2 ACL. Used when associate_waf is true."
  default     = []
}

########## Statement Rules

variable "managed_rule_group_statement_rules" {
  type = list(object({
    name            = string
    priority        = string
    override_action = string
    managed_rule_group_statement = list(object({
      name        = string
      vendor_name = string
      excluded_rule = list(object({
        name = string
      }))
    }))
  }))
}


variable "rate_based_statement_rules" {
  type = list(object({
    name     = string
    priority = string
    rate_based_statement = object({
      aggregate_key_type = string
      limit              = string
      scope_down_statement = object({
        byte_match_statement = object({
          positional_constraint = string
          search_string         = string
          field_to_match = object({
            uri_path = object({
            })
          })
          text_transformation = object({
            priority = string
            type     = string
          })
        })
      })
    })
  }))
}

########## Rate limit rule implemented on regional alb waf

variable "rate_limit" {
  type        = number
  description = "Rate limit for WAFv2 (requests per 5-minute window per IP). Set to 0 to disable rate limiting."
  default     = 0
}

variable "rate_limit_exception_list" {
  type        = list(string)
  description = "List of IP CIDR addresses to exclude from rate limiting"
  default     = []
}

variable "rate_limit_bypass_header_name" {
  type        = string
  description = "Header name whose presence (with matching value) bypasses rate limiting. Leave empty to disable. Must be set together with rate_limit_bypass_header_value."
  default     = ""
}

variable "rate_limit_bypass_header_value" {
  type        = string
  description = "Expected header value for rate limit bypass. Must be set together with rate_limit_bypass_header_name. Note: marked sensitive to redact from CLI output, but the value is still stored in plaintext in Terraform state and visible in the AWS WAF console. Treat it as a low-sensitivity token for controlling test traffic, not as a credential."
  default     = ""
  sensitive   = true
}

variable "associated_alb_arns" {
  type        = set(string)
  description = "Set of alb arns to associate the waf with"
  default     = []
}

variable "positional_constraint" {
  type        = string
  description = "Positional constraint"
  default     = "EXACTLY"
}

variable "enable_aws_managed_common_rules" {
  type        = bool
  description = "Enable AWS Managed Rules Common Rule Set by default. Provides protection against common web exploits."
  default     = true
}

variable "search_string" {
  type        = string
  description = "Search string"
  default     = "/charge"
}
