# Environment and Project Configuration
variable "environment" {
  description = "Environment name (e.g., prod, staging)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "veworld"
}

variable "project" {
  description = "Project name for Terraform tags"
  type        = string
  default     = "veworld"
}

# CloudFront Distribution Configuration
variable "mainnet_origin_domain" {
  description = "Origin domain for mainnet CloudFront distribution"
  type        = string
  default     = "mainnet.live.prod.veworld.vechain.org"
}

variable "testnet_origin_domain" {
  description = "Origin domain for testnet CloudFront distribution"
  type        = string
  default     = "testnet.live.prod.veworld.vechain.org"
}

variable "mainnet_certificate_arn" {
  description = "SSL certificate ARN for mainnet CloudFront distribution"
  type        = string
  default     = "arn:aws:acm:us-east-1:905964754131:certificate/392c3bd4-5a0d-43b0-a204-1307fc140749"
}

variable "testnet_certificate_arn" {
  description = "SSL certificate ARN for testnet CloudFront distribution"
  type        = string
  default     = "arn:aws:acm:us-east-1:905964754131:certificate/8b66d985-6d28-46fe-9a31-c4160c736fed"
}

variable "mainnet_cnames" {
  description = "List of CNAMEs for mainnet CloudFront distribution"
  type        = list(string)
  default     = ["indexer.mainnet.vechain.org"]
}

variable "testnet_cnames" {
  description = "List of CNAMEs for testnet CloudFront distribution"
  type        = list(string)
  default     = ["indexer.testnet.vechain.org"]
}

# Cache Policy Configuration
variable "default_cache_policy_name" {
  description = "Name for the default cache policy"
  type        = string
  default     = "veworld_default_cache_policy"
}

variable "default_headers_policy_name" {
  description = "Name for the default headers policy"
  type        = string
  default     = "veworld_default_header_policy"
}

variable "hourly_cache_policy_name" {
  description = "Name for the hourly cache policy"
  type        = string
  default     = "veworld_hourly_cache_policy"
}

variable "day_cache_policy_name" {
  description = "Name for the day cache policy"
  type        = string
  default     = "veworld_day_cache_policy"
}

variable "weekly_cache_policy_name" {
  description = "Name for the weekly cache policy"
  type        = string
  default     = "veworld_weekly_cache_policy"
}

variable "monthly_cache_policy_name" {
  description = "Name for the monthly cache policy"
  type        = string
  default     = "veworld_monthly_cache_policy"
}

# TTL Configuration
variable "default_ttl_seconds" {
  description = "Default TTL in seconds for default cache policy"
  type        = number
  default     = 60
}

variable "hourly_ttl_seconds" {
  description = "TTL in seconds for hourly cache policy"
  type        = number
  default     = 300
}

variable "day_ttl_seconds" {
  description = "TTL in seconds for day cache policy"
  type        = number
  default     = 300
}

variable "weekly_ttl_seconds" {
  description = "TTL in seconds for weekly cache policy"
  type        = number
  default     = 300
}

variable "monthly_ttl_seconds" {
  description = "TTL in seconds for monthly cache policy"
  type        = number
  default     = 300
}

# Cache Behavior Configuration
variable "nft_holders_paths" {
  description = "Map of NFT holders API paths and their descriptions"
  type = map(string)
  default = {
    "1-hour"  = "/api/v1/stargate/nft-holders/historic/1-hour"
    "1-day"   = "/api/v1/stargate/nft-holders/historic/1-day"
    "1-week"  = "/api/v1/stargate/nft-holders/historic/1-week"
    "1-month" = "/api/v1/stargate/nft-holders/historic/1-month"
  }
}

variable "hourly_allowed_methods" {
  description = "Allowed HTTP methods for hourly cache behavior"
  type        = list(string)
  default     = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
}

variable "standard_allowed_methods" {
  description = "Allowed HTTP methods for standard cache behaviors"
  type        = list(string)
  default     = ["GET", "HEAD", "OPTIONS"]
}

variable "cached_methods" {
  description = "HTTP methods to cache"
  type        = list(string)
  default     = ["GET", "HEAD"]
}

variable "viewer_protocol_policy" {
  description = "Viewer protocol policy for CloudFront"
  type        = string
  default     = "redirect-to-https"
}

# WAF Configuration
variable "enable_waf" {
  description = "Enable WAF for CloudFront distributions"
  type        = bool
  default     = true
}

variable "waf_rate_limit" {
  description = "Rate limit for WAF (requests per 5-minute period)"
  type        = number
  default     = 5000
}

variable "waf_rate_limit_exceptions" {
  description = "List of IP addresses/ranges to exclude from rate limiting"
  type        = list(string)
  default     = []
}

variable "waf_cloudfront_enable" {
  description = "Enable CloudFront association for WAF"
  type        = bool
  default     = true
}

variable "waf_logs_enable" {
  description = "Enable WAF logging"
  type        = bool
  default     = false
}

variable "waf_logs_s3_enable" {
  description = "Enable S3 logging for WAF"
  type        = bool
  default     = false
}

variable "waf_logs_retention" {
  description = "WAF logs retention period in days"
  type        = number
  default     = 30
}

variable "waf_scope" {
  description = "WAF scope (CLOUDFRONT or REGIONAL)"
  type        = string
  default     = "CLOUDFRONT"
}

variable "waf_associate" {
  description = "Whether to associate WAF with resources"
  type        = bool
  default     = true
}

# WAF Managed Rules Configuration
variable "waf_managed_rules" {
  description = "List of AWS managed rule groups to enable"
  type = list(object({
    name            = string
    priority        = number
    override_action = string
    rule_group_name = string
    vendor_name     = string
    excluded_rules  = list(string)
  }))
  default = [
    {
      name            = "AWS-AWSManagedRulesAmazonIpReputationList"
      priority        = 1
      override_action = "none"
      rule_group_name = "AWSManagedRulesAmazonIpReputationList"
      vendor_name     = "AWS"
      excluded_rules  = []
    },
    {
      name            = "AWS-AWSManagedRulesCommonRuleSet"
      priority        = 2
      override_action = "none"
      rule_group_name = "AWSManagedRulesCommonRuleSet"
      vendor_name     = "AWS"
      excluded_rules  = []
    },
    {
      name            = "AWS-AWSManagedRulesKnownBadInputsRuleSet"
      priority        = 3
      override_action = "none"
      rule_group_name = "AWSManagedRulesKnownBadInputsRuleSet"
      vendor_name     = "AWS"
      excluded_rules  = []
    },
    {
      name            = "AWS-AWSManagedRulesSQLiRuleSet"
      priority        = 4
      override_action = "none"
      rule_group_name = "AWSManagedRulesSQLiRuleSet"
      vendor_name     = "AWS"
      excluded_rules  = []
    }
  ]
}

# Module Source Configuration
variable "cloudfront_module_source" {
  description = "Git source for CloudFront module"
  type        = string
  default     = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
}

variable "policies_module_source" {
  description = "Git source for CloudFront policies module"
  type        = string
  default     = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
}

variable "waf_module_source" {
  description = "Git source for WAF module"
  type        = string
  default     = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//waf?ref=cloudfront-changes"
}
