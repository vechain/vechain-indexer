module "ecr" {
  count  = var.is_create_repo ? 1 : 0
  source = "../ecr"

  project  = var.project
  app_name = var.app_name
  env      = var.env

  enable                    = true
  enable_private_ecr        = true
  enable_scan_configuration = var.scan_all_repo_images

  # Scanning configuration
  scan_type           = "BASIC"
  scan_frequency      = "SCAN_ON_PUSH"
  scan_filter_pattern = "*"
  scan_filter_type    = "WILDCARD"
}

resource "aws_ecs_cluster" "ecs_cluster" {
  name = lower("${var.env}-${var.project}-cluster")
  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = {
    Name        = "${var.env}-${var.project}-cluster"
    Environment = var.env
    Project     = var.project
    Terraform   = "true"
  }
}
