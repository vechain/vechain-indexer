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
# AWS SSM Config Manager
############################################################################################################

resource "aws_config_config_rule" "config_rule" {
  count = startswith(local.env.environment, "prod") ? 0 : 1
  name  = "${local.env.environment}-${var.project}-mongodb-config-rule"

  source {
    owner             = "AWS"
    source_identifier = "S3_BUCKET_VERSIONING_ENABLED"
  }

  depends_on = [aws_config_configuration_recorder.config_recorder]
}

resource "aws_config_configuration_recorder" "config_recorder" {
  count    = startswith(local.env.environment, "prod") ? 0 : 1
  name     = "example"
  role_arn = aws_iam_role.config_role.arn
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["config.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "config_role" {
  name               = "${local.env.environment}-${var.project}-mongodb-config-role"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

data "aws_iam_policy_document" "config_policy" {
  statement {
    effect    = "Allow"
    actions   = ["config:Put*"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "config_role_policy" {
  name   = "${local.env.environment}-${var.project}-mongodb-config-role-policy"
  role   = aws_iam_role.config_role.id
  policy = data.aws_iam_policy_document.config_policy.json
}

############################################################################################################
# MongoDB security group
############################################################################################################
resource "aws_security_group" "mongodb_sg" {
  # Temporary measure to avoid deployment of new blue/green services from affecting existing prod resources
  for_each    = "${local.env.environment == "prod" || local.env.environment == "dev" ? local.env.enabled_nets : {}}"
  name        = "${local.env.environment}-mongodb-${each.key}-sg"
  description = "Allow required ingress and egress for the mongodb"
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id
  ingress {
    from_port   = 8
    to_port     = 0
    protocol    = "icmp"
    cidr_blocks = [local.env.cidr]
    description = "ping service"
  }

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [module.ecs-lb-service[each.key].security_group_alb_id, module.ecs-service[each.key].security_group_ecs_service_id]

    description = "mongodb service"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "allow all outbound traffic"
  }

  tags = {
    Name        = "${local.env.environment}-mongodb-sg"
    Environment = local.env.environment
    Application = "mongodb"
    Terraform   = "true"
  }
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

resource "aws_ssm_parameter" "mongo_index_password" {
  count = (var.mongo_credential_trigger != "" ? 0 : 1)
  name  = "/${local.env.environment}/${var.project}/mongo_index_password"
  type  = "SecureString"
  value = random_password.mongo_index_password[0].result
}

resource "aws_ssm_parameter" "mongo_api_password" {
  count = (var.mongo_credential_trigger != "" ? 0 : 1)
  name  = "/${local.env.environment}/${var.project}/mongo_api_password"
  type  = "SecureString"
  value = random_password.mongo_api_password[0].result
}
############################################################################################################
# MongoDB instance
############################################################################################################

data "aws_ami" "AwsFilteredImage" {
  most_recent = true
  filter {
    name   = "name"
    values = [var.ami_name_filter]
  }
}

resource "aws_cloudwatch_log_group" "mongo_log_group" {
  for_each          = local.env.enabled_nets
  name              = "${local.env.environment}-mongodb-${each.key}"
  retention_in_days = 30
  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_instance" "mongodb_cluster" {
  for_each                    = { for i, v in local.env.enabled_nets : i => v.mongodb if v.mongodb.type == "ec2" }
  associate_public_ip_address = var.mongodb_enable_public_ip
  #ami                        = data.aws_ami.AwsFilteredImage.id
  #ami                        = each.value.mongodb.ami
  ami = "ami-0c4c1d9ab1e204bd7"
  /*  instance_type           = each.value.mongodb.instance_type
*/
  instance_type           = each.value.instance_type
  iam_instance_profile    = aws_iam_instance_profile.ssm_instance_profile.name
  vpc_security_group_ids  = [aws_security_group.mongodb_sg[each.key].id]
  subnet_id               = data.terraform_remote_state.vpc.outputs.database_subnets[0]
  disable_api_termination = true
  user_data = base64encode(
    templatefile("${path.module}/templates/user-data.sh", {
      admin_username   = "admin",
      admin_password   = "${aws_ssm_parameter.mongo_admin_password[0].value}",
      indexer_username = "indexer",
      indexer_password = "${aws_ssm_parameter.mongo_index_password[0].value}",
      api_username     = "api",
      api_password     = "${aws_ssm_parameter.mongo_api_password[0].value}",
      awsregion        = "${local.env.region}",
      hostname         = "mongodb-${each.key}"
      log_group        = aws_cloudwatch_log_group.mongo_log_group[each.key].name
  }))


  ### root volume for instances ###
  root_block_device {
    volume_size           = 20
    volume_type           = "gp2"
    encrypted             = false
    delete_on_termination = true
  }

  ebs_block_device {
    device_name = "/dev/sdg"
    /*    volume_size           = each.value.mongodb.data_volume_size
    volume_type           = each.value.mongodb.data_volume_type
    iops                  = each.value.mongodb.data_volume_iops
*/
    volume_size           = each.value.data_volume_size
    volume_type           = each.value.data_volume_type
    iops                  = each.value.data_volume_iops
    encrypted             = false
    delete_on_termination = true
  }

  # Enable SSM
  metadata_options {
    http_endpoint               = "enabled"
    http_put_response_hop_limit = 1
    http_tokens                 = "required"
  }

  tags = {
    Name        = "${local.env.environment}-mongodb-${each.key}"
    Environment = local.env.environment
    Application = "mongodb"
    Terraform   = "true"
    backup      = "Backup-dev"
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

############################################################################################################
# Local R53 entry for mongodb node
############################################################################################################

resource "aws_route53_record" "mongodb_node" {
  for_each = { for i, v in local.env.enabled_nets : i => v.mongodb if v.mongodb.type == "ec2" }
  zone_id  = module.ecs-lb-service[each.key].private_zone_id
  name     = each.value.fqdn
  type     = "A"
  records  = [aws_instance.mongodb_cluster[each.key].private_ip]
  ttl      = "300"
}
