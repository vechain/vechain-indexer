############################################################################################################
# EC2 backed single dockerised mongodb instance
############################################################################################################

variable "enable_ssm" {
  default = true
}

variable "ami_ssm_parameter" {
  description = "SSM parameter name for the AMI ID. For Amazon Linux AMI SSM parameters see [reference](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-public-parameters-ami.html)"
  type        = string
  default     = "/aws/service/ami-amazon-linux-latest/amzn2-ami-minimal-hvm-arm64-ebs"
}

variable "ami_name_filter" {
  description = "AMI ID to use for the instance. If not provided, the latest Amazon Linux AMI will be used"
  type        = string
  default     = "amzn2-ami-minimal-hvm-*-arm64-ebs"
}

variable "mongodb_enable_public_ip" {
  default = "false"
}

variable "mongo_credential_trigger" {
  description = "Trigger to create new mongo credentials, if empty, credentials will be created"
  type        = string
  default     = ""
  sensitive   = true
}

############################################################################################################
# MongoDB credentials
############################################################################################################

resource "random_password" "mongo_admin_password" {
  count   = 1
  length  = 16
  special = true
  # Same as default string, but without @, as that is not allowed in RDS
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "random_password" "mongo_index_password" {
  count   = 1
  length  = 16
  special = true
  # Same as default string, but without @, as that is not allowed in RDS
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "random_password" "mongo_api_password" {
  count   = 1
  length  = 16
  special = true
  # Same as default string, but without @, as that is not allowed in RDS
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

data "aws_ssm_parameter" "mongo_admin_password" {
  count = (var.mongo_credential_trigger != "" ? 1 : 0)
  name  = "/${local.env.environment}/${var.project}/mongo_admin_password"
}

resource "aws_ssm_parameter" "mongo_admin_password" {
  count = (var.mongo_credential_trigger != "" ? 0 : 1)
  name  = "/${local.env.environment}/${var.project}/mongo_admin_password"
  type  = "SecureString"
  value = random_password.mongo_admin_password[0].result
}

############################################################################################################
# MongoDB instance
############################################################################################################

data "aws_ami" "AwsFilteredImage" {
  most_recent = true
  owners      = ["self", "amazon"]
  filter {
    name   = "name"
    values = [var.ami_name_filter]
  }
}

resource "aws_cloudwatch_log_group" "mongo_log_group" {
  for_each          = local.env.enabled_nets
  name              = "${local.env.project}-${local.env.environment}-mongodb-${each.key}"
  retention_in_days = 30
  lifecycle {
    prevent_destroy = false
  }
}

############################################################################################################
# IAM role for SSM
############################################################################################################
resource "aws_iam_role" "ssm_role" {
  name = "${local.env.environment}-ssm-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = [
            "ssm.amazonaws.com",
            "sts.amazonaws.com",
            "ec2.amazonaws.com",
          ]
        }
      }
    ]
  })
}

############################################################################################################
# IAM policy for SSM
############################################################################################################
resource "aws_iam_policy" "ssm_policy" {
  name        = "${local.env.environment}-ssm-policy"
  description = "Allow SSM access to EC2 instances"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:Describe*",
          "ssm:GetconnectionStatus",
          "ssm:GetDeployablePatchSnapshotForInstance",
          "ssm:GetDocument",
          "ssm:GetManifest",
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetServiceSetting",
          "ssm:List*",
          "ssm:PutInventory",
          "ssm:PutComplianceItems",
          "ssm:PutConfigurePackageResult",
          "ssm:ResetServiceSetting",
          "ssm:UpdateAssociationStatus",
          "ssm:UpdateInstanceAssociationStatus",
          "ssm:UpdateServiceSetting",
          "ssmmessages:CreateControlChannel",
          "ssmmessages:CreateDataChannel",
          "ssmmessages:OpenControlChannel",
          "ssmmessages:OpenDataChannel",
          "s3:GetEncryptionConfiguration"
        ]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "ssm:StartSession"
        Resource = "arn:aws:ec2:eu-west-1:937628727224:instance/*"
      },
      {
        Effect = "Allow"
        Action = [
          "ssm:ResumeSession",
          "ssm:TerminateSession"
        ]
        Resource = "arn:aws:ssm:*:*:session/*"
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey"
        ]
        Resource = "arn:aws:kms:eu-west-1:937628727224:key/3b077bf4-7686-4cef-96a3-ab2ea86e4608"
      },
      {
        Effect = "Allow"
        Action = [
          "iam:PassRole",
          "iam:AttachRolePolicy",
          "iam:DeleteRole"
        ]
        Resource = "arn:aws:iam::445211916558:role/aws-reserved/sso.amazonaws.com/eu-west-1/AWSReservedSSO_AdministratorAccess_22d9e46e544e5c87",
        Condition = {
          StringEquals = {
            "iam:PassedToService" : [
              "ssm.amazonaws.com"
            ]
          }
        }
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:Describe*",
          "ecr:Get*",
          "ecr:List*"
        ]
        Resource = "arn:aws:ecr:eu-west-1:937628727224:repository/veworld/mongo-setup-script"
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "*"
      }
    ]
  })
}

############################################################################################################
# IAM role policy attachment for SSM
############################################################################################################
resource "aws_iam_role_policy_attachment" "ssm_role_policy_attachment" {
  role       = aws_iam_role.ssm_role.name
  policy_arn = aws_iam_policy.ssm_policy.arn
}

############################################################################################################
# IAM managed policy attachment for SSM
############################################################################################################
resource "aws_iam_role_policy_attachment" "mongo_ssm_policy_attachment" {
  role       = aws_iam_role.ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedEC2InstanceDefaultPolicy"
}

############################################################################################################
# IAM instance profile for SSM
############################################################################################################
resource "aws_iam_instance_profile" "ssm_instance_profile" {
  name = "${local.env.environment}-ssm-instance-profile"
  role = aws_iam_role.ssm_role.name
}
