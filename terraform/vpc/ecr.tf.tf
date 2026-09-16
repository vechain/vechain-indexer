# ECR Repository for backend service

module "ecr-api" {
  source               = "../modules/ecr"
  project              = local.env.application
  app_name             = "api"
  image_tag_mutability = "MUTABLE"
  scan_on_push         = false
  encryption_type      = "KMS"
  max_image_count      = 50
}

# ECR Repository for frontend service

module "ecr-indexer" {
  source               = "../modules/ecr"
  project              = local.env.application
  app_name             = "indexer"
  image_tag_mutability = "MUTABLE"
  scan_on_push         = false
  encryption_type      = "KMS"
  max_image_count      = 50
}
