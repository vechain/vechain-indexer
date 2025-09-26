# Independent Shared Module Provider Configuration

terraform {
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
    key    = "shared/veworld-indexer-cloudfront.tfstate"
    region = "eu-west-1"
  }
}

# Main AWS provider
provider "aws" {
  region = local.env.region
  default_tags {
    tags = {
      Terraform   = "true"
      Environment = local.env.environment
      Project     = local.env.project_name
    }
  }
}

# US East 1 provider for CloudFront/WAF resources
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags {
    tags = {
      Terraform   = "true"
      Environment = local.env.environment
      Project     = local.env.project_name
    }
  }
}

provider "awscc" {
  region = local.env.region
}

provider "github" {}

provider "random" {}
