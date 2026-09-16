# Cloudwatch Dashboard Module

# Usage


```
bash

data "aws_region" "current" {}

provider "aws" {
  profile = var.profile
  region  = var.region
}

locals {
  dashboard_body = {
    "widgets": [
        {
            "height": 6,
            "width": 8,
            "y": 0,
            "x": 0,
            "type": "explorer",
            "properties": {
                "metrics": [
                    {
                        "metricName": "CPUUtilization",
                        "resourceType": "AWS::EC2::Instance",
                        "stat": "Maximum"
                    }
                ],
                "aggregateBy": {
                    "key": "InstanceType",
                    "func": "MAX"
                },
                "labels": [
                    {
                        "key": "State",
                        "value": "running"
                    }
                ],
                "widgetOptions": {
                    "legend": {
                        "position": "bottom"
                    },
                    "view": "timeSeries",
                    "rowsPerPage": 8,
                    "widgetsPerRow": 2
                },
                "period": 60,
                "stat": "Average"
                "region": data.aws_region.current.name,
                "title": "EC2|CPU Utilization"
            }
        }
    ]
}

module "dashboard" {
  source                      = "../../../cloudwatchdashboard"
  create_cloudwatch_dashboard = true
  dashboard_name              = "node-hosting-dashboard"
  dashboard_body              = jsonencode(local.dashboard_body)
}

```