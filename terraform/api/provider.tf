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
    # respective AWS accounts. The {{{ENV}}} placeholder is replaced manually (dev/prod)
    bucket = "veworld-indexer-terraform-state-{{{ENV}}}"
    key    = "veworld-indexer-api.tfstate"
    region = "eu-west-1"
    workspace_key_prefix = "workspaces"
  }
}

provider "aws" {
  region = local.env.region
  default_tags {
    tags = {
      Terraform = "true"
      Project = var.project
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
      Project = var.project
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

# Import outputs from the vpc module
data "terraform_remote_state" "vpc" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-{{{ENV}}}"
    key    = "workspaces/${local.env.environment}/veworld-indexer-vpc.tfstate"
    region = "eu-west-1"
  }
}
