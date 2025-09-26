terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  backend "s3" {
    bucket = "veworld-indexer-terraform-state-prod" 
    key                  = "veworld-indexer-cloudfront.tfstate"
    region               = "eu-west-1"
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
