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
    key                  = "veworld-indexer-api.tfstate"
    region               = "eu-west-1"
    workspace_key_prefix = "workspaces"
  }
}

data "aws_secretsmanager_secret_version" "atlas_api_keys" {
  secret_id = local.env.enabled_nets.main.mongodb.secret_arn
}
provider "mongodbatlas" {
  public_key  = jsondecode(data.aws_secretsmanager_secret_version.atlas_api_keys.secret_string)["public_key"]
  private_key = jsondecode(data.aws_secretsmanager_secret_version.atlas_api_keys.secret_string)["private_key"]
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

data "external" "git" {
  program = ["git", "log", "--pretty=format:{ \"sha\": \"%H\" }", "-1", "HEAD"]
}

# Import outputs from the vpc module
data "terraform_remote_state" "vpc" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state${startswith(local.env.environment, "prod-") ? "-prod" : ""}"
    key    = "workspaces/${startswith(local.env.environment, "prod-") ? "prod" : local.env.environment}/veworld-indexer-vpc.tfstate"
    region = "eu-west-1"
  }
}

variable "network" {
  type        = string
  description = "The network to deploy to (optional). Allowed values are 'mainnet' or 'testnet' or 'both'. This variable is required."

  validation {
    condition     = var.network == "mainnet" || var.network == "testnet" || var.network == "both"
    error_message = "Invalid value for network. Allowed values are 'mainnet' or 'testnet' or 'both', e.g. `terraform apply --var=network=mainnet`"
  }
  default = "both"
}
