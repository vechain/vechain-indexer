variable "cache_policy" {
  type        = string
  description = "Name of the cache policy"
}

variable "headers_policy" {
  type        = string
  description = "Name of the headers policy"
  default     = "default_headers_policy"
}

variable "min_ttl" {
  type        = number
  description = "Min cache policy time to live"
}

variable "default_ttl" {
  type        = number
  description = "default cache policy time to live"
}

variable "max_ttl" {
  type        = number
  description = "Max cache policy time to live"
}

variable "enable_brotli" {
  type    = bool
  default = false
}

variable "enable_gzip" {
  type    = bool
  default = false
}

variable "create_header_policy" {
  type    = number
  default = 0
}

variable "cookie_behavior" {
  type        = string
  description = "cookie behavior"
  default     = "none"
}

variable "header_behavior" {
  type        = string
  description = "header behavior"
  default     = "none"
}

variable "header_items" {
  type        = list(string)
  description = "Headers in the cache key. Required when header_behavior is whitelist."
  default     = []
}

variable "query_string_behavior" {
  type        = string
  description = "query string behavior"
  default     = "none"
}
