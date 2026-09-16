###################################################################
# Cloudwatch Dashboard
###################################################################

data "aws_region" "current" {}

resource "aws_cloudwatch_dashboard" "dashboard" {
  count = var.create_cloudwatch_dashboard ? 1 : 0

  dashboard_name = var.dashboard_name
  dashboard_body = var.dashboard_body
}
