data "aws_vpc" "ct_vpc_id" {
  filter {
    name   = "tag:Name"
    values = ["aws-controltower-VPC", "${local.env.environment}-${var.project}-vpc"]
  }
}

data "aws_subnets" "ct_pub_subnets" {
  filter {
    name   = "tag:Name"
    values = ["aws-controltower-PublicSubnet*"]
  }
}

data "aws_subnets" "ct_priv_subnets" {
  filter {
    name   = "tag:Name"
    values = ["aws-controltower-PrivateSubnet*"]
  }
}

data "aws_subnets" "dev_pub_subnets" {
  filter {
    name   = "tag:Name"
    values = ["dev-veworld-vpc-public-eu-west-1*"]
  }
}

resource "aws_security_group" "sg_dev" {
  name        = "veworld-sg-dev"
  description = "Security group for development environment"
  vpc_id      = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "veworld-sg-dev"
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_security_group" "sg_prod_blue" {
  name        = "veworld-sg-prod-blue"
  description = "Security group for production blue environment"
  vpc_id      = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "veworld-sg-prod-blue"
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_security_group" "sg_prod_green" {
  name        = "veworld-sg-prod-green"
  description = "Security group for production green environment"
  vpc_id      = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "veworld-sg-prod-green"
    Environment = local.env.environment
    Project     = var.project
  }
}

resource "aws_security_group" "sg_prod" {
  name        = "veworld-sg-prod"
  description = "Security group for production environment"
  vpc_id      = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "veworld-sg-prod"
    Environment = local.env.environment
    Project     = var.project
  }
}



data "aws_subnets" "dev_priv_subnets" {
  filter {
    name   = "tag:Name"
    values = ["dev-veworld-vpc-private-eu-west-1*"]
  }
}

data "aws_subnets" "database_subnets" {
  filter {
    name   = "tag:Name"
    values = ["*-veworld-vpc-db-eu-west-1*"]
  }
}

variable "project" {
  default = "veworld"
}

variable "app_name" {
  default = ""
}

module "vpc" {
  count   = local.env.environment == "prod" ? 0 : 1
  source  = "terraform-aws-modules/vpc/aws"
  version = "v4.0.1"
  cidr    = local.env.cidr
  name    = "${local.env.environment}-${var.project}-vpc"
  azs     = local.env.azs
  # Compact subnetting up to 4 AZs, by up to 4 subnets by x/24 cidr blocks neatly fits in /20 172.31.0.0/16 fits 16 of them
  # AZ's are offset by 4 with subnets if each AZ sequential neighbours
  public_subnets         = local.env.public_subnets
  private_subnets        = local.env.private_subnets
  database_subnets       = local.env.database_subnets
  enable_nat_gateway     = true
  single_nat_gateway     = false
  one_nat_gateway_per_az = true

  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Environment = local.env.environment
    Terraform   = "true"
    Project     = var.project
  }
}

# Modules
module "vpclogs_cloudwatch" {
  source                                          = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//logs?ref=v.1.0.2"
  environment                                     = local.env.environment
  create_flow_log_cloudwatch_log_group            = "true"
  create_flow_log_cloudwatch_iam_role             = "true"
  flow_log_log_format                             = null
  flow_log_traffic_type                           = "ALL"
  vpc_id                                          = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
  flow_log_max_aggregation_interval               = 600
  flow_log_destination_type                       = "cloud-watch-logs"
  flow_log_file_format                            = "plain-text"
  flow_log_hive_compatible_partitions             = "false"
  flow_log_per_hour_partition                     = "false"
  flow_log_cloudwatch_log_group_retention_in_days = 30
  flow_log_cloudwatch_log_group_kms_key_id        = null
  vpc_flow_log_permissions_boundary               = null
  enable_flow_log_cloudwatch                      = "true"
}

module "vpclogs_s3" {
  source                              = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//logs?ref=v.1.0.2"
  environment                         = local.env.environment
  app_name                            = var.project
  flow_log_log_format                 = null
  flow_log_traffic_type               = "ALL"
  vpc_id                              = local.env.environment == "prod" ? data.aws_vpc.ct_vpc_id.id : module.vpc[0].vpc_id
  flow_log_max_aggregation_interval   = 600
  flow_log_destination_type           = "s3"
  flow_log_file_format                = "plain-text"
  flow_log_hive_compatible_partitions = "false"
  flow_log_per_hour_partition         = "false"
  vpc_flow_log_permissions_boundary   = null
  enable_flow_log_s3                  = "false"
  create_s3_bucket                    = "false"
}
