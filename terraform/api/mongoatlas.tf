module "mongoatlas-main-net" {
  source = "git::git@github.com:vechainfoundation/terraform_infrastructure_modules.git//mongoatlas?ref=terragrunt/simple-mongodb-atlas"

  secret_id  = local.env.enabled_nets.main.mongodb.secret_arn
  project_id = local.env.mongoatlas_project_id # MongoDB Atlas project ID

  create_api_key = false

  mongodbatlas_audit_enabled               = false
  mongodbatlas_audit_filter                = "{ 'atype': 'authenticate', 'param': {   'user': 'auditAdmin',   'db': 'admin',   'mechanism': 'SCRAM-SHA-1' }}"
  mongodbatlas_audit_authorization_success = false // Enabling Audit authorization successes can severely impact cluster performance. Enable this option with caution.

  enable_cluster               = startswith(local.env.environment, "prod-") ? true : false
  cluster_name                 = "${local.env.environment}-Mainnet"
  disk_size_gb                 = local.env.enabled_nets.main.mongodb.disk_size_gb
  num_shards                   = 1
  cloud_backup                 = true
  cluster_type                 = "REPLICASET"
  auto_scaling_disk_gb_enabled = true
  provider_name                = "AWS"
  provider_disk_iops           = try(local.env.enabled_nets.main.mongodb.iops, null)
  provider_volume_type         = "STANDARD"
  provider_instance_size_name  = local.env.enabled_nets.main.mongodb.cluster_tier
  mongo_db_major_version       = "6"
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

  project_ip_access_lists = (startswith(local.env.environment, "prod-") ? [
    {
      ip_address = "${split("/", data.terraform_remote_state.vpc.outputs.vpc_ipv4)[0]}/32"
      comment    = "AWS VPC"
    }
  ] : [])

  enable_mongodbatlas_backup_schedule = startswith(local.env.environment, "prod-") ? true : false
  mongodbatlas_backup_schedule_config = {
    reference_hour_of_day    = 7
    reference_minute_of_hour = 00
    restore_window_days      = 1

    policy_item_hourly = {
      frequency_interval = 1
      retention_unit     = "days"
      retention_value    = 1
    }
    policy_item_daily = {
      frequency_interval = 1
      retention_unit     = "days"
      retention_value    = 1
    }
    policy_item_weekly = {
      frequency_interval = 1
      retention_unit     = "weeks"
      retention_value    = 1
    }
    policy_item_monthly = {
      frequency_interval = 1
      retention_unit     = "months"
      retention_value    = 1
    }
  }
}

module "mongoatlas-test-net" {
  source = "git::git@github.com:vechainfoundation/terraform_infrastructure_modules.git//mongoatlas?ref=terragrunt/simple-mongodb-atlas"

  secret_id  = local.env.enabled_nets.test.mongodb.secret_arn
  project_id = local.env.mongoatlas_project_id # MongoDB Atlas project ID

  create_api_key = false

  mongodbatlas_audit_enabled               = false
  mongodbatlas_audit_filter                = "{ 'atype': 'authenticate', 'param': {   'user': 'auditAdmin',   'db': 'admin',   'mechanism': 'SCRAM-SHA-1' }}"
  mongodbatlas_audit_authorization_success = false // Enabling Audit authorization successes can severely impact cluster performance. Enable this option with caution.

  enable_cluster               = startswith(local.env.environment, "prod-") ? true : false
  cluster_name                 = "${local.env.environment}-Testnet"
  disk_size_gb                 = local.env.enabled_nets.test.mongodb.disk_size_gb
  num_shards                   = 1
  cloud_backup                 = true
  cluster_type                 = "REPLICASET"
  auto_scaling_disk_gb_enabled = true
  provider_name                = "AWS"
  provider_disk_iops           = try(local.env.enabled_nets.test.mongodb.iops, null)
  provider_volume_type         = "STANDARD"
  provider_instance_size_name  = local.env.enabled_nets.test.mongodb.cluster_tier
  mongo_db_major_version       = "6"
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

  project_ip_access_lists = (startswith(local.env.environment, "prod-") ? [
    {
      ip_address = split("/", data.terraform_remote_state.vpc.outputs.vpc_ipv4)[0]
      comment    = "AWS VPC"
    }
  ] : [])

  enable_mongodbatlas_backup_schedule = startswith(local.env.environment, "prod-") ? true : false
  mongodbatlas_backup_schedule_config = {
    reference_hour_of_day    = 7
    reference_minute_of_hour = 00
    restore_window_days      = 1

    policy_item_hourly = {
      frequency_interval = 1
      retention_unit     = "days"
      retention_value    = 1
    }
    policy_item_daily = {
      frequency_interval = 1
      retention_unit     = "days"
      retention_value    = 1
    }
    policy_item_weekly = {
      frequency_interval = 1
      retention_unit     = "weeks"
      retention_value    = 1
    }
    policy_item_monthly = {
      frequency_interval = 1
      retention_unit     = "months"
      retention_value    = 1
    }
  }
}
