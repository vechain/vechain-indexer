terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      #version = "3.75.1"
    }
  }
  backend "s3" {
    bucket = "veworld-indexer-terraform-state"
    key    = "dev-vpc-veworld-indexer.tfstate"
    region = "eu-west-1"
  }
}

provider "aws" {
  profile = local.env.workspace_account
  region = local.env.region
}

//create aws provider alias for us-east-1
provider "aws" {
  alias  = "us_east_1"
  profile = local.env.workspace_account
  region = "us-east-1"
}

provider "awscc" {
  profile = local.env.workspace_account
  region = local.env.region
}

provider "github" {}

provider "random" {}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_elb_service_account" "default" {}
