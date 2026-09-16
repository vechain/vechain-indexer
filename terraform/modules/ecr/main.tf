terraform {
  required_version = ">= 1.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 4.0.0"
    }
  }
}

locals {
  principals_readonly_access_non_empty = length(var.principals_readonly_access) > 0 ? true : false
  principals_full_access_non_empty     = length(var.principals_full_access) > 0 ? true : false
  ecr_need_policy                      = length(var.principals_full_access) + length(var.principals_readonly_access) > 0 ? true : false
}

##########################################
# Private Repository
##########################################
resource "aws_ecr_repository" "ecr" {
  count                = var.enable && var.enable_private_ecr ? 1 : 0
  name                 = lower("${var.project}/${var.app_name}")
  image_tag_mutability = var.image_tag_mutability
  encryption_configuration {
    encryption_type = var.encryption_type
  }
  image_scanning_configuration {
    scan_on_push = var.scan_on_push
  }
}

##########################################
# ECR Lifecycle Policy
##########################################

resource "aws_ecr_lifecycle_policy" "private" {
  count      = var.enable && var.enable_private_ecr ? 1 : 0
  repository = aws_ecr_repository.ecr[0].name

  policy = <<EOF
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Remove untagged images",
      "selection": {
        "tagStatus": "untagged",
        "countType": "sinceImagePushed",
        "countUnit": "days",
        "countNumber": ${var.max_untagged_image_count}
      },
      "action": {
        "type": "expire"
      }
    },
    {
      "rulePriority": 2,
      "description": "Never expire images tagged 'prod*'",
      "selection": {
        "tagStatus": "tagged",
        "tagPatternList": ["prod*"],
        "countType": "imageCountMoreThan",
        "countNumber": 999
      },
      "action": {
        "type": "expire"
      }
    },
    {
      "rulePriority": 3,
      "description": "Rotate images when reach ${var.max_image_count} images stored (except prod)",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": ${var.max_image_count}
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
EOF
}

##########################################
# ECR IAM Policy
##########################################
data "aws_iam_policy_document" "resource_readonly_access_private" {
  statement {
    sid    = "ReadonlyAccess"
    effect = "Allow"

    principals {
      type = "AWS"

      identifiers = var.principals_readonly_access
    }

    actions = [
      "ecr:GetRegistryPolicy",
      "ecr:DescribeImageScanFindings",
      "ecr:GetLifecyclePolicyPreview",
      "ecr:GetDownloadUrlForLayer",
      "ecr:DescribeRegistry",
      "ecr:DescribePullThroughCacheRules",
      "ecr:DescribeImageReplicationStatus",
      "ecr:GetAuthorizationToken",
      "ecr:ListTagsForResource",
      "ecr:ListImages",
      "ecr:BatchGetRepositoryScanningConfiguration",
      "ecr:GetRegistryScanningConfiguration",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetRepositoryPolicy",
      "ecr:GetLifecyclePolicy",
    ]
  }
}

data "aws_iam_policy_document" "resource_full_access_private" {
  statement {
    sid    = "FullAccess"
    effect = "Allow"

    principals {
      type = "AWS"

      identifiers = var.principals_full_access
    }

    actions = [
      "ecr:*"
    ]
  }
}

data "aws_iam_policy_document" "resource_private" {
  count                     = var.enable ? 1 : 0
  source_policy_documents   = [local.principals_readonly_access_non_empty ? join("", data.aws_iam_policy_document.resource_readonly_access_private.*.json) : join("", data.aws_iam_policy_document.empty.*.json)]
  override_policy_documents = [local.principals_full_access_non_empty ? join("", data.aws_iam_policy_document.resource_full_access_private.*.json) : join("", data.aws_iam_policy_document.empty.*.json)]
}

resource "aws_ecr_repository_policy" "private" {
  count      = var.enable && var.enable_private_ecr && local.ecr_need_policy ? 1 : 0
  repository = aws_ecr_repository.ecr[0].name
  policy     = data.aws_iam_policy_document.resource_private[0].json
}

data "aws_iam_policy_document" "empty" {
  count = var.enable ? 1 : 0
}

##########################################
# ECR Scanning Configuration
##########################################

resource "aws_ecr_registry_scanning_configuration" "configuration" {
  count     = var.enable && var.enable_scan_configuration ? 1 : 0
  scan_type = var.scan_type

  rule {
    scan_frequency = var.scan_frequency
    repository_filter {
      filter      = var.scan_filter_pattern
      filter_type = var.scan_filter_type
    }
  }
}
