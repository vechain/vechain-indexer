################################################################################
# Atlas Backup Export to S3
#
# These resources live in the vpc module (shared, never torn down) so that
# backup exports survive blue/green cluster teardowns. The api module's
# backup schedules reference the export_bucket_id via remote state.
################################################################################

resource "aws_s3_bucket" "atlas_backups" {
  count  = local.env.environment == "prod" ? 1 : 0
  bucket = "veworld-indexer-atlas-backups"

  tags = {
    Name        = "veworld-indexer-atlas-backups"
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "atlas_backups" {
  count  = local.env.environment == "prod" ? 1 : 0
  bucket = aws_s3_bucket.atlas_backups[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "atlas_backups" {
  count  = local.env.environment == "prod" ? 1 : 0
  bucket = aws_s3_bucket.atlas_backups[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "atlas_backups" {
  count  = local.env.environment == "prod" ? 1 : 0
  bucket = aws_s3_bucket.atlas_backups[0].id

  rule {
    id     = "expire-after-2-days"
    status = "Enabled"

    expiration {
      days = 2
    }
  }
}

################################################################################
# Atlas Cloud Provider Access (IAM trust relationship)
################################################################################

resource "mongodbatlas_cloud_provider_access_setup" "backup_export" {
  count         = local.env.environment == "prod" ? 1 : 0
  project_id    = local.env.mongoatlas_project_id
  provider_name = "AWS"
}

resource "aws_iam_role" "atlas_backup_export" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "veworld-atlas-backup-export"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        AWS = mongodbatlas_cloud_provider_access_setup.backup_export[0].aws_config[0].atlas_aws_account_arn
      }
      Action = "sts:AssumeRole"
      Condition = {
        StringEquals = {
          "sts:ExternalId" = mongodbatlas_cloud_provider_access_setup.backup_export[0].aws_config[0].atlas_assumed_role_external_id
        }
      }
    }]
  })

  tags = {
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_iam_role_policy" "atlas_backup_export_s3" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "atlas-backup-export-s3-access"
  role  = aws_iam_role.atlas_backup_export[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ]
        Resource = aws_s3_bucket.atlas_backups[0].arn
      },
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.atlas_backups[0].arn}/*"
      }
    ]
  })
}

resource "mongodbatlas_cloud_provider_access_authorization" "backup_export" {
  count      = local.env.environment == "prod" ? 1 : 0
  project_id = local.env.mongoatlas_project_id
  role_id    = mongodbatlas_cloud_provider_access_setup.backup_export[0].role_id

  aws {
    iam_assumed_role_arn = aws_iam_role.atlas_backup_export[0].arn
  }
}

################################################################################
# Register the S3 bucket with Atlas as an export destination
################################################################################

resource "mongodbatlas_cloud_backup_snapshot_export_bucket" "main" {
  count          = local.env.environment == "prod" ? 1 : 0
  project_id     = local.env.mongoatlas_project_id
  iam_role_id    = mongodbatlas_cloud_provider_access_authorization.backup_export[0].role_id
  bucket_name    = aws_s3_bucket.atlas_backups[0].id
  cloud_provider = "AWS"
}
