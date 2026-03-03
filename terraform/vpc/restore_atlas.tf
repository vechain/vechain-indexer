################################################################################
# Restore MongoDB Atlas Infrastructure
#
# IAM role, instance profile, and security group for the temporary EC2
# Used with a pre-provisioned EC2 instance to restore MongoDB Atlas from s3
################################################################################

resource "aws_iam_role" "restore_instance" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "veworld-atlas-restore-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = {
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_iam_role_policy_attachment" "restore_ssm" {
  count      = local.env.environment == "prod" ? 1 : 0
  role       = aws_iam_role.restore_instance[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "restore_s3_read" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "atlas-restore-s3-read"
  role  = aws_iam_role.restore_instance[0].id

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
          "s3:GetObject"
        ]
        Resource = "${aws_s3_bucket.atlas_backups[0].arn}/*"
      }
    ]
  })
}

resource "aws_iam_role_policy" "restore_secrets_read" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "atlas-restore-secrets-read"
  role  = aws_iam_role.restore_instance[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue"
      ]
      Resource = "arn:aws:secretsmanager:${local.env.region}:*:secret:*mongo*"
    }]
  })
}

resource "aws_iam_instance_profile" "restore_instance" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "veworld-atlas-restore-instance"
  role  = aws_iam_role.restore_instance[0].name

  tags = {
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_security_group" "restore_instance" {
  count       = local.env.environment == "prod" ? 1 : 0
  name        = "veworld-atlas-restore-sg"
  description = "Security group for temporary DR restore EC2 instances"
  vpc_id      = data.aws_vpc.ct_vpc_id.id

  egress {
    description = "HTTPS outbound for S3, SSM, Atlas API"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "MongoDB Atlas database connections"
    from_port   = 27017
    to_port     = 27017
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "veworld-atlas-restore-sg"
    Environment = local.env.environment
    Project     = var.project
  }
}
