variable "network" {
  type        = string
  description = "The network that will receive live traffic. Allowed values are 'mainnet' or 'testnet'. This variable is required."
  validation {
    condition     = var.network == "mainnet" || var.network == "testnet" || var.network == "all"
    error_message = "Invalid value for network. Allowed values are 'mainnet', 'testnet', or 'all', e.g. `terraform apply --var=network=mainnet`"
  }
  default = "all"
}

data "aws_route53_zone" "veworld_public_zone_lookup" {
  name         = "${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  private_zone = false
}

# Existing Route53 records (current state)
data "aws_route53_record" "mainnet_live_current" {
  zone_id = data.aws_route53_zone.veworld_public_zone_lookup.zone_id
  name    = "mainnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
}

data "aws_route53_record" "testnet_live_current" {
  zone_id = data.aws_route53_zone.veworld_public_zone_lookup.zone_id
  name    = "testnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
}

data "aws_route53_record" "mainnet_dead_current" {
  zone_id = data.aws_route53_zone.veworld_public_zone_lookup.zone_id
  name    = "mainnet.dead.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
}

data "aws_route53_record" "testnet_dead_current" {
  zone_id = data.aws_route53_zone.veworld_public_zone_lookup.zone_id
  name    = "testnet.dead.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
}

locals {
  # Which networks are being switched this run?
  update_mainnet = var.network == "mainnet" || var.network == "all"
  update_testnet = var.network == "testnet" || var.network == "all"

  # Target LB hostnames per colour
  blue_mainnet_lb  = data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet
  green_mainnet_lb = data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet
  blue_testnet_lb  = data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet
  green_testnet_lb = data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet

  # Identify current live colour by comparing the record value to the blue lb.
  current_mainnet_is_blue = data.aws_route53_record.mainnet_live_current.records[0] == local.blue_mainnet_lb
  current_testnet_is_blue = data.aws_route53_record.testnet_live_current.records[0] == local.blue_testnet_lb

  # Compute desired live records
  live_mainnet_lb = local.update_mainnet ? (
    local.current_mainnet_is_blue ? local.green_mainnet_lb : local.blue_mainnet_lb
  ) : data.aws_route53_record.mainnet_live_current.records[0]

  live_testnet_lb = local.update_testnet ? (
    local.current_testnet_is_blue ? local.green_testnet_lb : local.blue_testnet_lb
  ) : data.aws_route53_record.testnet_live_current.records[0]

  # Compute desired dead records (opposite of live)
  dead_mainnet_lb = local.update_mainnet ? (
    local.current_mainnet_is_blue ? try(local.blue_mainnet_lb, "place.holder.domain") : try(local.green_mainnet_lb, "place.holder.domain")
  ) : data.aws_route53_record.mainnet_dead_current.records[0]

  dead_testnet_lb = local.update_testnet ? (
    local.current_testnet_is_blue ? try(local.blue_testnet_lb, "place.holder.domain") : try(local.green_testnet_lb, "place.holder.domain")
  ) : data.aws_route53_record.testnet_dead_current.records[0]
}

resource "aws_route53_zone" "veworld_public_zone" {
  count = startswith(local.env.environment, "prod") ? 1 : 0
  name  = "${local.env.environment}.${local.env.application}.${local.env.root_domain}"

  tags = {
    Name        = "${local.env.environment}.${local.env.application}.${local.env.root_domain}"
    Environment = local.env.environment
    Application = local.env.application
    Project     = "veworld-indexer"
    Terraform   = "true"
  }
}

resource "aws_route53_record" "mainnet_live" {
  count   = startswith(local.env.environment, "prod") ? 1 : 0
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "mainnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.live_mainnet_lb]
}

resource "aws_route53_record" "testnet_live" {
  count   = startswith(local.env.environment, "prod") ? 1 : 0
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "testnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.live_testnet_lb]
}

resource "aws_route53_record" "mainnet_dead" {
  count   = startswith(local.env.environment, "prod") ? 1 : 0
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "mainnet.dead.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.dead_mainnet_lb]
}

resource "aws_route53_record" "testnet_dead" {
  count   = startswith(local.env.environment, "prod") ? 1 : 0
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "testnet.dead.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.dead_testnet_lb]
}
