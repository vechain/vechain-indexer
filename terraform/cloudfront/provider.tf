# Main CloudFront Infrastructure Configuration
# This configuration supports multiple workspaces: shared, staging, prod

terraform {
  required_version = ">= 1.5"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    awscc = {
      source  = "hashicorp/awscc"
      version = "~> 0.70.0"
    }
    github = {
      source  = "integrations/github"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.4"
    }
  }
  
  backend "s3" {
    bucket = "veworld-indexer-terraform-state-prod"
    key    = "cloudfront/terraform.tfstate"
    region = "eu-west-1"
    
    # Workspace support - state files will be stored as:
    # cloudfront/env:/workspace-name/terraform.tfstate
    workspace_key_prefix = "cloudfront"
  }
}

# Local variables for workspace configuration
locals {
  workspace = terraform.workspace
  
  # Load configuration based on workspace
  env_config = yamldecode(file("environments/${local.workspace}.yml"))
  
  # Workspace-specific settings
  is_shared   = local.workspace == "shared"
  is_staging  = local.workspace == "staging"
  is_prod     = local.workspace == "prod"
  
}

# Providers
provider "aws" {
  region = local.env_config.region
  
  default_tags {
    tags = {
      Terraform   = "true"
      Project     = local.env_config.project_name
      Environment = local.env_config.environment
      Workspace   = local.workspace
    }
  }
}

provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  
  default_tags {
    tags = {
      Terraform   = "true"
      Project     = local.env_config.project_name
      Environment = local.env_config.environment
      Workspace   = local.workspace
    }
  }
}

provider "awscc" {
  region = local.env_config.region
}

provider "github" {}

provider "random" {}

# Data sources
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
data "aws_elb_service_account" "default" {}

# Remote state data source for shared resources (used by staging and prod workspaces)
# Note: This will fail if shared workspace hasn't been deployed yet
data "terraform_remote_state" "shared" {
  count = local.is_shared ? 0 : 1
  
  backend = "s3"
  
  config = {
    bucket = "veworld-indexer-terraform-state-prod"
    key    = "cloudfront/shared/cloudfront/terraform.tfstate"
    region = "eu-west-1"
  }
}

# Remote state data source for staging (used by prod workspace for continuous deployment)
data "terraform_remote_state" "staging" {
  count = local.is_prod && local.env_config.enable_continuous_deployment ? 1 : 0
  
  backend = "s3"
  
  config = {
    bucket = "veworld-indexer-terraform-state-prod"
    key    = "cloudfront/staging/cloudfront/terraform.tfstate"
    region = "eu-west-1"
  }
}

