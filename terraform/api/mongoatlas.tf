################################################################################
# MongoDB Atlas Advanced Clusters
################################################################################

resource "mongodbatlas_advanced_cluster" "main_net" {
  count      = try(local.env.enabled_nets.main.mongodb.type, false) == "atlas" ? 1 : 0
  project_id = local.env.mongoatlas_project_id
  name       = "${local.env.environment}-Mainnet"

  cluster_type            = "REPLICASET"
  mongo_db_major_version  = "8"
  backup_enabled          = true
  retain_backups_enabled  = true

  replication_specs = [{
    region_configs = [{
      provider_name = "AWS"
      region_name   = "EU_WEST_1"
      priority      = 7

      electable_specs = {
        instance_size = local.env.enabled_nets.main.mongodb.cluster_tier
        node_count    = 3
      }

      auto_scaling = {
        disk_gb_enabled            = true
        compute_enabled            = false
        compute_scale_down_enabled = false
      }
    }]
  }]

  lifecycle {
    ignore_changes = [
      replication_specs[0].region_configs[0].electable_specs[0].disk_size_gb,
    ]
  }
}

resource "mongodbatlas_advanced_cluster" "test_net" {
  count      = try(local.env.enabled_nets.test.mongodb.type, false) == "atlas" ? 1 : 0
  project_id = local.env.mongoatlas_project_id
  name       = "${local.env.environment}-Testnet"

  cluster_type            = "REPLICASET"
  mongo_db_major_version  = "8"
  backup_enabled          = true
  retain_backups_enabled  = true

  replication_specs = [{
    region_configs = [{
      provider_name = "AWS"
      region_name   = "EU_WEST_1"
      priority      = 7

      electable_specs = {
        instance_size = local.env.enabled_nets.test.mongodb.cluster_tier
        node_count    = 3
      }

      auto_scaling = {
        disk_gb_enabled            = true
        compute_enabled            = false
        compute_scale_down_enabled = false
      }
    }]
  }]

  lifecycle {
    ignore_changes = [
      replication_specs[0].region_configs[0].electable_specs[0].disk_size_gb,
    ]
  }
}

################################################################################
# Backup Schedules (production only)
################################################################################

resource "mongodbatlas_cloud_backup_schedule" "main_net" {
  count        = startswith(local.env.environment, "prod") && try(local.env.enabled_nets.main.mongodb.type, false) == "atlas" ? 1 : 0
  project_id   = local.env.mongoatlas_project_id
  cluster_name = mongodbatlas_advanced_cluster.main_net[0].name

  reference_hour_of_day    = 7
  reference_minute_of_hour = 0
  restore_window_days      = 1

  auto_export_enabled                  = true
  use_org_and_group_names_in_export_prefix = true

  policy_item_daily {
    frequency_interval = 1
    retention_unit     = "days"
    retention_value    = 7
  }

  policy_item_weekly {
    frequency_interval = 1
    retention_unit     = "weeks"
    retention_value    = 3
  }

  policy_item_monthly {
    frequency_interval = 1
    retention_unit     = "months"
    retention_value    = 3
  }

  export {
    export_bucket_id = data.terraform_remote_state.vpc.outputs.atlas_export_bucket_id
    frequency_type   = "daily"
  }
}

resource "mongodbatlas_cloud_backup_schedule" "test_net" {
  count        = startswith(local.env.environment, "prod-") && try(local.env.enabled_nets.test.mongodb.type, false) == "atlas" ? 1 : 0
  project_id   = local.env.mongoatlas_project_id
  cluster_name = mongodbatlas_advanced_cluster.test_net[0].name

  reference_hour_of_day    = 7
  reference_minute_of_hour = 0
  restore_window_days      = 1

  auto_export_enabled                  = true
  use_org_and_group_names_in_export_prefix = true

  policy_item_daily {
    frequency_interval = 1
    retention_unit     = "days"
    retention_value    = 7
  }

  policy_item_weekly {
    frequency_interval = 1
    retention_unit     = "weeks"
    retention_value    = 3
  }

  policy_item_monthly {
    frequency_interval = 1
    retention_unit     = "months"
    retention_value    = 3
  }

  export {
    export_bucket_id = data.terraform_remote_state.vpc.outputs.atlas_export_bucket_id
    frequency_type   = "daily"
  }
}

################################################################################
# Alerts (non-dev environments only)
################################################################################

data "aws_secretsmanager_secret_version" "slack_token" {
  count     = startswith(local.env.environment, "dev") ? 0 : 1
  secret_id = local.env.slack_secret_arn
}

resource "mongodbatlas_alert_configuration" "host_mongot_crashing_oom" {
  count      = startswith(local.env.environment, "dev") ? 0 : 1
  project_id = local.env.mongoatlas_project_id
  event_type = "HOST_MONGOT_CRASHING_OOM"
  enabled    = true

  notification {
    type_name     = "GROUP"
    interval_min  = 60
    delay_min     = 0
    email_enabled = true
    roles         = ["GROUP_CLUSTER_MANAGER"]
  }

  notification {
    type_name    = "SLACK"
    interval_min = 60
    delay_min    = 0
    api_token    = data.aws_secretsmanager_secret_version.slack_token[0].secret_string
    channel_name = "veworld-x-devops"
  }
}

# TODO: Remove this resource after state migration has been applied to all environments.
# It exists only to receive the moved state from the old testnet module alert.
resource "mongodbatlas_alert_configuration" "host_mongot_crashing_oom_legacy" {
  count      = startswith(local.env.environment, "dev") ? 0 : 1
  project_id = local.env.mongoatlas_project_id
  event_type = "HOST_MONGOT_CRASHING_OOM"
  enabled    = false

  notification {
    type_name     = "GROUP"
    interval_min  = 60
    delay_min     = 0
    email_enabled = true
    roles         = ["GROUP_CLUSTER_MANAGER"]
  }
}

################################################################################
# State migration: moved blocks
################################################################################

moved {
  from = module.mongoatlas-main-net.mongodbatlas_cluster.cluster[0]
  to   = mongodbatlas_advanced_cluster.main_net[0]
}

moved {
  from = module.mongoatlas-test-net.mongodbatlas_cluster.cluster[0]
  to   = mongodbatlas_advanced_cluster.test_net[0]
}

moved {
  from = module.mongoatlas-main-net.mongodbatlas_cloud_backup_schedule.schedule[0]
  to   = mongodbatlas_cloud_backup_schedule.main_net[0]
}

moved {
  from = module.mongoatlas-test-net.mongodbatlas_cloud_backup_schedule.schedule[0]
  to   = mongodbatlas_cloud_backup_schedule.test_net[0]
}

moved {
  from = module.mongoatlas-main-net.mongodbatlas_alert_configuration.dynamic_alert["alert_type_1"]
  to   = mongodbatlas_alert_configuration.host_mongot_crashing_oom[0]
}

moved {
  from = module.mongoatlas-test-net.mongodbatlas_alert_configuration.dynamic_alert["alert_type_1"]
  to   = mongodbatlas_alert_configuration.host_mongot_crashing_oom_legacy[0]
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
  count              = startswith(local.env.environment, "prod") ? 1 : 0
  username           = "api-${local.env.environment}"
  password           = random_password.api_db_user_password.result
  project_id         = local.env.mongoatlas_project_id
  auth_database_name = "admin"

  roles {
    role_name     = "readAnyDatabase"
    database_name = "admin"
  }

  scopes {
    name = "${local.env.environment}-Mainnet"
    type = "CLUSTER"
  }
  scopes {
    name = "${local.env.environment}-Testnet"
    type = "CLUSTER"
  }
}
resource "mongodbatlas_database_user" "indexer_db_user" {
  count              = startswith(local.env.environment, "prod") ? 1 : 0
  username           = "indexer-${local.env.environment}"
  password           = random_password.indexer_db_user_password.result
  project_id         = local.env.mongoatlas_project_id
  auth_database_name = "admin"

  roles {
    role_name     = "readWriteAnyDatabase"
    database_name = "admin"
  }

  scopes {
    name = "${local.env.environment}-Mainnet"
    type = "CLUSTER"
  }
  scopes {
    name = "${local.env.environment}-Testnet"
    type = "CLUSTER"
  }
}
