terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      #version = "3.75.1"
    }
  }
  backend "s3" {
    bucket = "veworld-indexer-terraform-state"
    key    = "veworld-indexer-api.tfstate"
    region = "eu-west-1"
    workspace_key_prefix = "workspaces"
  }
}

provider "aws" {
  region = local.env.region
  default_tags {
    tags = {
      "Terraform" = "true"
      "Environment" = local.env.environment
      "Project" = var.project
      "Application" = var.app_name
    }
  }
}

//create aws provider alias for us-east-1
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags {
    tags = {
      "Terraform" = "true"
      "Environment" = local.env.environment
      "Project" = var.project
      "Application" = var.app_name
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
    bucket = "veworld-indexer-terraform-state"
    key    = "workspaces/${local.env.environment}/veworld-indexer-vpc.tfstate"
    region = "eu-west-1"
  }
}
