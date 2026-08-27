variable "origin_domain" {
  type        = string
  description = "principal domain to cache"
}

variable "cnames" {
  type        = list(string)
  description = "List of additional alternate cnames for cache"
  default     = []
}

variable "certificate_arn" {
  type = string
}


variable "ordered_cache_behaviors" {
  type = list(object({
    path_pattern             = string
    target_origin_id         = string
    cache_policy_id          = string
    headers_policy_id        = string
    origin_request_policy_id = string
    allowed_methods          = list(string)
    cached_methods           = list(string)
    viewer_protocol_policy   = string
  }))
  default = []
}

variable "cache_policy_id" {
  type        = string
  description = "ID of the cache policy"
}

variable "headers_policy_id" {
  type        = string
  description = "ID of the headers policy"
}
variable "origin_request_policy_id" {
  type        = string
  description = "ID of the request policy"
}


variable "waf_web_acl" {
  type        = string
  description = "acl of attached waf"
  default     = ""
}
variable "continuous_deployment_policy_id" {
  type        = string
  description = "continuous_deployment_policy_id"
  default     = null
}

variable "staging" {
  type        = bool
  description = "continuous_deployment_policy_id"
  default     = false
}
