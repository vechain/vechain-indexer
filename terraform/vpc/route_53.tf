variable "live_color_mainnet" {
  type        = string
  description = "The COLOUR that should RECEIVE live mainnet traffic after this apply. Allowed values: 'prod-blue' or 'prod-green'."

  validation {
    condition     = var.live_color_mainnet == "prod-blue" || var.live_color_mainnet == "prod-green"
    error_message = "Invalid value for live_color_mainnet: must be 'prod-blue' or 'prod-green'"
  }
}

variable "live_color_testnet" {
  type        = string
  description = "The COLOUR that should RECEIVE live testnet traffic after this apply. Allowed values: 'prod-blue' or 'prod-green'."

  validation {
    condition     = var.live_color_testnet == "prod-blue" || var.live_color_testnet == "prod-green"
    error_message = "Invalid value for live_color_testnet: must be 'prod-blue' or 'prod-green'"
  }
}

locals {
  blue_mainnet_lb  = data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet
  green_mainnet_lb = data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet
  blue_testnet_lb  = data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet
  green_testnet_lb = data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet
  yellow_mainnet_lb = data.terraform_remote_state.api-yellow.outputs.load_balancer_domain_mainnet
  yellow_testnet_lb = data.terraform_remote_state.api-yellow.outputs.load_balancer_domain_testnet

  # Desired records directly from the requested colours
  live_mainnet_lb = var.live_color_mainnet == "prod-blue" ? local.blue_mainnet_lb : local.green_mainnet_lb
  live_testnet_lb = var.live_color_testnet == "prod-blue" ? local.blue_testnet_lb : local.green_testnet_lb

  # Dead records are always the opposite colour (placeholder if environment not yet deployed)
  dead_mainnet_lb = var.live_color_mainnet == "prod-blue" ? try(local.green_mainnet_lb, "place.holder.domain") : try(local.blue_mainnet_lb, "place.holder.domain")
  dead_testnet_lb = var.live_color_testnet == "prod-blue" ? try(local.green_testnet_lb, "place.holder.domain") : try(local.blue_testnet_lb, "place.holder.domain")
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
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "mainnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.live_mainnet_lb]
}

resource "aws_route53_record" "testnet_live" {
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "testnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.live_testnet_lb]
}

resource "aws_route53_record" "mainnet_dead" {
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "mainnet.dead.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.dead_mainnet_lb]
}

resource "aws_route53_record" "testnet_dead" {
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "testnet.dead.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.dead_testnet_lb]
}

# Route53 records for yellow environment (in a separate, non-prod hosted zone)
resource "aws_route53_record" "yellow_mainnet" {
  zone_id = local.env.yellow_env_hosted_zone_id
  name    = "mainnet.yellow.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.yellow_mainnet_lb]
}

resource "aws_route53_record" "yellow_testnet" {
  zone_id = local.env.yellow_env_hosted_zone_id
  name    = "testnet.yellow.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.yellow_testnet_lb]
}
