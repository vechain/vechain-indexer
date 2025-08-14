terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      #version = "3.75.1"
    }
    mongodbatlas = {
      source  = "mongodb/mongodbatlas"
      version = ">= 1.12.0"
    }
  }
  backend "s3" {
    # The states of DEV and PROD environments are stored in separate S3 buckets in their
    # respective AWS accounts. i.e for dev - replace prod in bucket name with dev
    key                  = "veworld-indexer-vpc.tfstate"
    region               = "eu-west-1"
    workspace_key_prefix = "workspaces"
  }
}

data "aws_secretsmanager_secret_version" "atlas_api_keys" {
  secret_id = local.env.mongodb_secret_arn
}
provider "mongodbatlas" {
  public_key  = jsondecode(data.aws_secretsmanager_secret_version.atlas_api_keys.secret_string)["public_key"]
  private_key = jsondecode(data.aws_secretsmanager_secret_version.atlas_api_keys.secret_string)["private_key"]
}

# Import outputs from the api module
# blue green concept only applies to prod
data "terraform_remote_state" "api-blue" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-${terraform.workspace}"
    key    = terraform.workspace == "prod" ? "workspaces/prod-blue/veworld-indexer-api.tfstate" : "workspaces/${terraform.workspace}/veworld-indexer-api.tfstate"
    region = "eu-west-1"
  }
}

# Import outputs from the api module
data "terraform_remote_state" "api-green" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-${terraform.workspace}"
    key    = terraform.workspace == "prod" ? "workspaces/prod-green/veworld-indexer-api.tfstate" : "workspaces/${terraform.workspace}/veworld-indexer-api.tfstate"
    region = "eu-west-1"
  }
}

provider "aws" {
  region = local.env.region
  default_tags {
    tags = {
      Terraform = "true"
      Project   = var.project
    }
  }
}

//create aws provider alias for us-east-1
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags {
    tags = {
      Terraform = "true"
      Project   = var.project
    }
  }
}

provider "awscc" {
  region = local.env.region
}

provider "github" {}

provider "random" {}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_elb_service_account" "default" {}
