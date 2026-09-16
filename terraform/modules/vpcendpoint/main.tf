#####################################################################
# VPC Endpoints - Type Interface
#####################################################################

data "aws_vpc_endpoint_service" "vpce_service" {
  for_each     = { for tuple in var.vpcendpoints_interfaces : tuple.id => tuple }
  service      = each.key
  service_type = "Interface"
}

resource "aws_vpc_endpoint" "vpce" {
  for_each = { for tuple in var.vpcendpoints_interfaces : tuple.id => tuple }

  vpc_id              = lookup(each.value, "vpc_id")
  service_name        = data.aws_vpc_endpoint_service.vpce_service[each.key].service_name
  vpc_endpoint_type   = "Interface"
  security_group_ids  = lookup(each.value, "security_group_ids", [])
  subnet_ids          = lookup(each.value, "subnet_ids", [])
  private_dns_enabled = lookup(each.value, "private_dns_enabled", false)

  tags = {
    Name        = strcontains(each.value.id, ".") ? replace(each.value.id, ".", "-") : each.value.id
    Env         = var.env
    Project     = var.project
    Application = var.app_name
  }
}
