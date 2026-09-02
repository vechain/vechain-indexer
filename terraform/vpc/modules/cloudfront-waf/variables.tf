variable "name" {
  type        = string
  description = "Web ACL name. Must match the existing name exactly — renaming forces replacement and detaches the ACL from its CloudFront distributions."
}

variable "env" {
  type        = string
  description = "Value for the Env/Environment/Workspace tags."
  default     = "shared"
}

variable "project_tag" {
  type        = string
  nullable    = true
  default     = null
  description = "Overrides the Project tag. Leave null to inherit the provider's default_tags — setting it to the same value the default already supplies makes Terraform reattribute the tag and produces a spurious diff."
}

variable "rate_limit" {
  type        = number
  description = "Requests per evaluation window per IP before the blanket rate rule blocks."
}

variable "block_ip_set_arn" {
  type        = string
  description = "ARN of the shared IP block list referenced by the block-ip-set rule."
}

variable "log_group_arn" {
  type        = string
  description = "CloudWatch log group ARN for WAF logs."
}

variable "logging_filter_block_only" {
  type        = bool
  description = "Keep only BLOCK-ed requests in the WAF logs and drop the rest."
  default     = false
}

variable "enable_python_ua_rate_limit" {
  type        = bool
  description = "Enable the stricter rate limit for python-requests user agents."
  default     = false
}

variable "python_ua_rate_limit" {
  type        = number
  description = "Requests per evaluation window per IP for python-requests user agents."
  default     = 50
}

variable "enable_high_rate_count_rule" {
  type        = bool
  description = "Add a COUNT-only rate rule flagging IPs above high_rate_count_limit. It never blocks — it measures what a tighter limit would catch."
  default     = false
}

variable "high_rate_count_limit" {
  type        = number
  description = "Requests per evaluation window per IP before the COUNT-only rule flags them. Set well under rate_limit; that gap is what the dry run measures."
  default     = 300
}

variable "logging_filter_include_count" {
  type        = bool
  description = "Also keep COUNT-matched requests in the WAF logs, not only BLOCKed ones. Every request from a flagged IP is logged, so read the counted volume before switching this on."
  default     = false
}

variable "rule_priorities" {
  type        = map(number)
  description = "Rule key to WAF priority. Priorities differ between the mainnet and testnet ACLs; they are passed in rather than normalised so the import produces an empty plan."
}

# Off by default: the live ACLs carry no scope_down on the rate rule.
variable "enable_rate_limit_exemptions" {
  type        = bool
  description = "Attach a scope_down_statement to the blanket rate rule so exempt IPs and callers presenting the bypass header are not counted."
  default     = false
}

variable "rate_limit_exempt_ipv4" {
  type        = list(string)
  description = "IPv4 CIDRs exempt from the blanket rate rule. An empty set never matches, so leaving this empty exempts nothing."
  default     = []
}

variable "rate_limit_exempt_ipv6" {
  type        = list(string)
  description = "IPv6 CIDRs exempt from the blanket rate rule. Prefer a /56 or /64 — WAF aggregates IPv6 more coarsely than /128, so single-address entries will not hold."
  default     = []
}

variable "rate_limit_bypass_header_name" {
  type        = string
  description = "Header whose presence with a matching value exempts a request from the blanket rate rule. Set together with rate_limit_bypass_header_value."
  default     = ""
}

variable "rate_limit_bypass_header_value" {
  type        = string
  sensitive   = true
  description = "Expected value for rate_limit_bypass_header_name. Redacted from CLI output but stored in plaintext in state and readable via wafv2 get-web-acl. Treat as a low-sensitivity token, not a credential."
  default     = ""
}
