# ECR Repository for backend service

module "ecr-api" {
  source                     = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecr?ref=v.3.1.21"
  project                    = local.env.application
  app_name                   = "api"
  image_tag_mutability       = "MUTABLE"
  scan_on_push               = false
  encryption_type            = "KMS"
  max_image_count            = 50
}

# ECR Repository for frontend service

module "ecr-indexer" {
  source               = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecr?ref=v.3.1.21"
  project              = local.env.application
  app_name             = "indexer"
  image_tag_mutability = "MUTABLE"
  scan_on_push         = false
  encryption_type      = "KMS"
  max_image_count      = 50
}

# ECR Repository for datadog agent

module "ecr-datadog" {
  source               = "git::git@github.com:/vechain/terraform_infrastructure_modules.git//ecr?ref=v.3.1.21"
  project              = local.env.application
  app_name             = "datadog-agent"
  image_tag_mutability = "MUTABLE"
  scan_on_push         = false
  encryption_type      = "KMS"
  max_image_count      = 2
}
