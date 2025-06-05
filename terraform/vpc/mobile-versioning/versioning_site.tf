module "versioning_site" {
  source = "git@github.com:vechain/terraform_infrastructure_modules.git//s3-static-cloudfront-hosting?ref=v.3.1.3"
  # vechainfoundation/devops.git//s3-cloudfront-hosting?"
  env           = local.env.environment
  project       = var.project
  domain_name   = local.env.domain_name
  origin_id     = local.env.origin_id
  bucket_prefix = local.env.bucket_prefix
  domain_zone   = local.env.domain_zone
  block_public  = false
  create_logging_bucket = false
  referer       = "random"
  
}
