terraform {
  # `use_lockfile = true` on the S3 backend below is S3-native locking
  # (conditional writes); GA in terraform 1.11.
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.11"
    }
  }

  backend "s3" {
    key                  = "veworld-indexer-observability.tfstate"
    region               = "eu-west-1"
    workspace_key_prefix = "workspaces"
    encrypt              = true
    use_lockfile         = true
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
