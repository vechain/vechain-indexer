terraform {
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

  # State locking is deliberately omitted here to match terraform/api and
  # terraform/vpc, which the repo's CI (setup-terraform pinned to 1.9.8
  # in .github/workflows/) currently expects. `use_lockfile = true` is
  # S3-native locking (GA in terraform 1.11) — worth adopting across the
  # repo once the toolchain is bumped, but out of scope for this stack.
  backend "s3" {
    key                  = "veworld-indexer-observability.tfstate"
    region               = "eu-west-1"
    workspace_key_prefix = "workspaces"
    encrypt              = true
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
