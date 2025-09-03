
# Modules

module "cloudfront" {
  source                 = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
  origin_domain    = "mainnet.live.prod.veworld.vechain.org"
  certificate_arn        = "arn:aws:acm:us-east-1:905964754131:certificate/392c3bd4-5a0d-43b0-a204-1307fc140749"
  cnames = ["indexer.mainnet.vechain.org"]
  ordered_cache_behaviors = [
    {
      path_pattern           =  "/api/v1/stargate/nft-holders/historic/1-hour",
      target_origin_id       = "origin-mainnet.live.prod.veworld.vechain.org"
      cache_policy_id        = module.hourly_cache_policy.cache_policy_id 
      headers_policy_id      = module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    },
    {
      path_pattern           = "/api/v1/stargate/nft-holders/historic/1-day",
      target_origin_id       = "origin-mainnet.live.prod.veworld.vechain.org"
      cache_policy_id        =  module.day_cache_policy.cache_policy_id 
      headers_policy_id      =  module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    },
     {
      path_pattern           = "/api/v1/stargate/nft-holders/historic/1-week",
      target_origin_id       = "origin-mainnet.live.prod.veworld.vechain.org"
      cache_policy_id        =  module.weekly_cache_policy.cache_policy_id 
      headers_policy_id      =  module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    },
     {
      path_pattern           = "/api/v1/stargate/nft-holders/historic/1-month",
      target_origin_id       = "origin-mainnet.live.prod.veworld.vechain.org"
      cache_policy_id        =  module.monthly_cache_policy.cache_policy_id 
      headers_policy_id      =  module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    }
  ]
   waf_web_acl = length(module.waf) > 0 ? module.waf[0].waf_arn : null
  cache_policy_id        = module.default_cache_policy.cache_policy_id 
  headers_policy_id      = module.default_cache_policy.headers_policy_id 
}

module "testnet_cloudfront" {
  source                    = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/non_s3_distribution?ref=cloudfront-changes"
  origin_domain             = "testnet.live.prod.veworld.vechain.org"
  certificate_arn           = "arn:aws:acm:us-east-1:905964754131:certificate/8b66d985-6d28-46fe-9a31-c4160c736fed"
  cnames = ["indexer.testnet.vechain.org"]
  ordered_cache_behaviors = [
    {
      path_pattern           =  "/api/v1/stargate/nft-holders/historic/1-hour",
      target_origin_id       = "origin-testnet.live.prod.veworld.vechain.org"
      cache_policy_id        = module.hourly_cache_policy.cache_policy_id 
      headers_policy_id      = module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    },
    {
      path_pattern           = "/api/v1/stargate/nft-holders/historic/1-day",
      target_origin_id       = "origin-testnet.live.prod.veworld.vechain.org"
      cache_policy_id        =  module.day_cache_policy.cache_policy_id 
      headers_policy_id      =  module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    },
     {
      path_pattern           = "/api/v1/stargate/nft-holders/historic/1-week",
      target_origin_id       = "origin-testnet.live.prod.veworld.vechain.org"
      cache_policy_id        =  module.weekly_cache_policy.cache_policy_id 
      headers_policy_id      =  module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    },
     {
      path_pattern           = "/api/v1/stargate/nft-holders/historic/1-month",
      target_origin_id       = "origin-testnet.live.prod.veworld.vechain.org"
      cache_policy_id        =  module.monthly_cache_policy.cache_policy_id 
      headers_policy_id      =  module.default_cache_policy.headers_policy_id 
      allowed_methods        = ["GET", "HEAD", "OPTIONS"]
      cached_methods         = ["GET", "HEAD"]
      viewer_protocol_policy = "redirect-to-https"
    }
  ]
  waf_web_acl = length(module.waf) > 0 ? module.waf[0].waf_arn : null
  cache_policy_id        = module.default_cache_policy.cache_policy_id 
  headers_policy_id      = module.default_cache_policy.headers_policy_id 
}

module "default_cache_policy" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy                        = "veworld_default_cache_policy"
  headers_policy                      = "veworld_default_header_policy"
  create_header_policy                = 1
  default_ttl                         = 60
  max_ttl                             = 60
  min_ttl                             = 60
}


module "hourly_cache_policy" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy                        = "veworld_hourly_cache_policy"
  create_header_policy                = 0
  default_ttl                         = 300
  max_ttl                             = 300
  min_ttl                             = 300
}

module "day_cache_policy" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy                        = "veworld_day_cache_policy"
  create_header_policy                = 0
  default_ttl                         = 300
  max_ttl                             = 300
  min_ttl                             = 300
}

module "weekly_cache_policy" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy                        = "veworld_weekly_cache_policy"
  create_header_policy                = 0
  default_ttl                         = 300
  max_ttl                             = 300
  min_ttl                             = 300
}

module "monthly_cache_policy" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudfront/policies?ref=cloudfront-changes"
  cache_policy                        = "veworld_monthly_cache_policy"
  create_header_policy                = 0
  default_ttl                         = 300
  max_ttl                             = 300
  min_ttl                             = 300
}

module "waf" {
  providers = {
    aws = aws.us_east_1
  }
  count                              =  1 
  source                             = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//waf?ref=cloudfront-changes"
  env                                = "prod"
  project_name                       = "veworld"
  waf_cloudfront_enable              = true
  logs_enable                        = false
  logs_s3_enable                     = false
  logs_retension                     = 30
  scope                              = "CLOUDFRONT"
  associate_waf                      = true
  rate_limit                         = 5000
  rate_limit_exception_list          = []
  
  managed_rule_group_statement_rules = [{
    name            = "AWS-AWSManagedRulesAmazonIpReputationList"
    priority        = 1
    override_action = "none"
    managed_rule_group_statement = [{
      name        = "AWSManagedRulesAmazonIpReputationList"
      vendor_name = "AWS"
      excluded_rule = []
      
      
    }]
  },{
  name            = "AWS-AWSManagedRulesCommonRuleSet"
    priority        = 2
    override_action = "none"
    managed_rule_group_statement = [{
      name        = "AWSManagedRulesCommonRuleSet"
      vendor_name = "AWS"
      excluded_rule = []
     
    }]
  },
  {
  name            = "AWS-AWSManagedRulesKnownBadInputsRuleSet"
    priority        = 3
    override_action = "none"
    managed_rule_group_statement = [{
      name        = "AWSManagedRulesKnownBadInputsRuleSet"
      vendor_name = "AWS"
      excluded_rule = []
      
    }]
  },
  {
  name            = "AWS-AWSManagedRulesSQLiRuleSet"
    priority        = 4
    override_action = "none"
    managed_rule_group_statement = [{
      name        = "AWSManagedRulesSQLiRuleSet"
      vendor_name = "AWS"
      excluded_rule = []
      
    }]
  }

  ]
}