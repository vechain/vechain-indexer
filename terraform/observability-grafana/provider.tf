terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    grafana = {
      source  = "grafana/grafana"
      version = "~> 3.0"
    }
  }

  backend "s3" {
    key                  = "veworld-indexer-observability-grafana.tfstate"
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

data "aws_secretsmanager_secret_version" "amg_sa_token" {
  secret_id = data.terraform_remote_state.observability.outputs.amg_sa_token_secret_arn
}

provider "grafana" {
  url  = "https://${data.terraform_remote_state.observability.outputs.amg_workspace_endpoint}"
  auth = data.aws_secretsmanager_secret_version.amg_sa_token.secret_string
}

data "terraform_remote_state" "observability" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-prod"
    key    = "workspaces/${terraform.workspace}/veworld-indexer-observability.tfstate"
    region = "eu-west-1"
  }
}
