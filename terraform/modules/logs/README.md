# Introduction

This module should be used along with vpc creation to enable vpclogs flow either to s3 bucket or a cloudwatch log group or to both as destination.

#Usage: To enable just cloudwatch log group
```
module "vpclogs_cloudwatch" {
  source                                          = "../../../vpclogs"
  environment                                     = "${terraform.workspace}"
  create_flow_log_cloudwatch_log_group            = "true"
  create_flow_log_cloudwatch_iam_role             = "true"
  flow_log_log_format                             = null
  flow_log_traffic_type                           = "ALL"
  vpc_id                                          = module.vpc.vpc_id
  flow_log_max_aggregation_interval               = 600
  flow_log_destination_type                       = "cloud-watch-logs"
  flow_log_file_format                            = "plain-text"
  flow_log_hive_compatible_partitions             = "false"
  flow_log_per_hour_partition                     = "false"
  flow_log_cloudwatch_log_group_retention_in_days = 30
  flow_log_cloudwatch_log_group_kms_key_id        = null
  vpc_flow_log_permissions_boundary               = null
  enable_flow_log_cloudwatch                      = "true"
}
```

To enable s3 as destination.
```
module "vpclogs_s3" {
  source                              = "../../../vpclogs"
  environment                         = "${terraform.workspace}"
  app_name                            = "Happydays"
  flow_log_log_format                 = null
  flow_log_traffic_type               = "ALL"
  vpc_id                              = module.vpc.vpc_id
  flow_log_max_aggregation_interval   = 600
  flow_log_destination_type           = "s3"
  flow_log_file_format                = "plain-text"
  flow_log_hive_compatible_partitions = "false"
  flow_log_per_hour_partition         = "false"
  vpc_flow_log_permissions_boundary   = null
  enable_flow_log_s3                  = "true"
  create_s3_bucket                    = "true"
  ```
  