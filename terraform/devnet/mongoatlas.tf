locals {
  mongodbatlas_backup_schedule = {
    reference_hour_of_day    = 7
    reference_minute_of_hour = 00
    restore_window_days      = 1

    policy_item_daily = {
      frequency_interval = 1
      retention_unit     = "days"
      retention_value    = 7
    }

    policy_item_weekly = {
      frequency_interval = 1
      retention_unit     = "weeks"
      retention_value    = 3
    }

    policy_item_monthly = {
      frequency_interval = 1
      retention_unit     = "months"
      retention_value    = 3
    }
  }
}

module "mongoatlas-dev-net" {
  source     = "git::git@github.com:vechainfoundation/terraform_infrastructure_modules.git//mongoatlas?ref=v.3.1.1"
  secret_id  = local.env.enabled_nets.dev.mongodb.secret_arn
  project_id = local.env.mongoatlas_project_id # MongoDB Atlas project ID

  create_api_key  = false
  slack_api_token = local.env.slack_secret_arn
  alerts = startswith(local.env.environment, "dev") ? {} : {
    alert_type_1 = {
      event_type = "HOST_MONGOT_CRASHING_OOM"
      enabled    = true

      notifications = [
        {
          type_name     = "GROUP"
          interval_min  = 60
          delay_min     = 0
          email_enabled = true
          roles         = ["GROUP_CHARTS_ADMIN", "GROUP_CLUSTER_MANAGER"]
        },
        {
          type_name          = "SLACK"
          interval_min       = 60
          delay_min          = 0
          slack_enabled      = true
          slack_channel_name = "veworld-x-devops"
          roles              = ["GROUP_CHARTS_ADMIN", "GROUP_CLUSTER_MANAGER"]
        }
      ]
    }
  }

  audit_enabled = false
  audit_config = {
    audit_filter                = "{ 'atype': 'authenticate', 'param': {   'user': 'auditAdmin',   'db': 'admin',   'mechanism': 'SCRAM-SHA-1' }}"
    audit_authorization_success = false // Enabling Audit authorization successes can severely impact cluster performance. Enable this option with caution.
  }

  enable_cluster = try(local.env.enabled_nets.dev.mongodb.type, false) == "atlas" ? true : false

  cluster_config = {
    cluster_name                 = "${local.env.environment}-devnet"
    disk_size_gb                 = local.env.enabled_nets.dev.mongodb.disk_size_gb
    num_shards                   = 1
    cloud_backup                 = true
    cluster_type                 = "REPLICASET"
    auto_scaling_disk_gb_enabled = true
    provider_name                = "AWS"
    provider_volume_type         = "STANDARD"
    provider_instance_size_name  = local.env.enabled_nets.dev.mongodb.cluster_tier
    mongo_db_major_version       = "8"

    auto_scaling_compute_enabled                    = true
    auto_scaling_compute_scale_down_enabled         = true
    provider_auto_scaling_compute_max_instance_size = local.env.enabled_nets.dev.mongodb.auto_scaling_compute_max_instance_size
    provider_auto_scaling_compute_min_instance_size = local.env.enabled_nets.dev.mongodb.auto_scaling_compute_min_instance_size

    replication_specs = [
      {
        num_shards = 1
        regions_config = [
          {
            region_name     = "EU_WEST_1"
            electable_nodes = 3
            priority        = 7
            read_only_nodes = 0
          },
        ]
      }
    ]
  }

  enable_mongodbatlas_backup_schedule = startswith(local.env.environment, "dev") ? false : true
  mongodbatlas_backup_schedule_config = local.mongodbatlas_backup_schedule
}



# Create Database Users in MongoDB Atlas and corresponding secrets in AWS Secrets Manager
# These secrets are used by the API and Indexer ECS services to connect to the MongoDB Atlas clusters

resource "random_password" "api_db_user_password" {
  length  = 12
  special = true
}
resource "random_password" "indexer_db_user_password" {
  length  = 12
  special = true
}

resource "aws_secretsmanager_secret" "api_db_user_secret" {
  name = "/${local.env.environment}/${local.env.project}/mongo_api_password"
}
resource "aws_secretsmanager_secret" "indexer_db_user_secret" {
  name = "/${local.env.environment}/${local.env.project}/mongo_indexer_password"
}

resource "aws_secretsmanager_secret_version" "api_db_user_secret_version" {
  secret_id     = aws_secretsmanager_secret.api_db_user_secret.id
  secret_string = random_password.api_db_user_password.result
}
resource "aws_secretsmanager_secret_version" "indexer_db_user_secret_version" {
  secret_id     = aws_secretsmanager_secret.indexer_db_user_secret.id
  secret_string = random_password.indexer_db_user_password.result
}

resource "mongodbatlas_database_user" "api_db_user" {
  count              = startswith(local.env.environment, "dev") ? 1 : 0
  username           = "api-${local.env.environment}"
  password           = random_password.api_db_user_password.result
  project_id         = local.env.mongoatlas_project_id
  auth_database_name = "admin"

  roles {
    role_name     = "readAnyDatabase"
    database_name = "admin"
  }

  scopes {
    name = "${local.env.environment}-devnet"
    type = "CLUSTER"
  }
}
resource "mongodbatlas_database_user" "indexer_db_user" {
  count              = startswith(local.env.environment, "dev") ? 1 : 0
  username           = "indexer-${local.env.environment}"
  password           = random_password.indexer_db_user_password.result
  project_id         = local.env.mongoatlas_project_id
  auth_database_name = "admin"

  roles {
    role_name     = "readWriteAnyDatabase"
    database_name = "admin"
  }

  scopes {
    name = "${local.env.environment}-devnet"
    type = "CLUSTER"
  }
}