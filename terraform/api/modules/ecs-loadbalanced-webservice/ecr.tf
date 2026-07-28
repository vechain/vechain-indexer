resource "aws_ecr_repository" "repo" {
  count = var.is_create_repo ? 1 : 0
  name  = lower("${var.project}/${var.app_name}")
}

resource "aws_ecr_registry_scanning_configuration" "configuration" {
  count     = var.scan_all_repo_images ? 1 : 0
  scan_type = "BASIC"

  rule {
    scan_frequency = "SCAN_ON_PUSH"
    repository_filter {
      filter      = "*"
      filter_type = "WILDCARD"
    }
  }
}
