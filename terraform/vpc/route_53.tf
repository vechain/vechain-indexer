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
  placeholder_lb = "place.holder.domain"

  # try() has to sit here, not at the point of use: a torn-down colour makes the
  # local itself fail, which a downstream try() cannot rescue.
  blue_mainnet_lb  = try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet, local.placeholder_lb)
  green_mainnet_lb = try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet, local.placeholder_lb)
  blue_testnet_lb  = try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet, local.placeholder_lb)
  green_testnet_lb = try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet, local.placeholder_lb)

  # Desired records directly from the requested colours
  live_mainnet_lb = var.live_color_mainnet == "prod-blue" ? local.blue_mainnet_lb : local.green_mainnet_lb
  live_testnet_lb = var.live_color_testnet == "prod-blue" ? local.blue_testnet_lb : local.green_testnet_lb

  # Dead records are always the opposite colour (placeholder if environment not yet deployed)
  dead_mainnet_lb = var.live_color_mainnet == "prod-blue" ? local.green_mainnet_lb : local.blue_mainnet_lb
  dead_testnet_lb = var.live_color_testnet == "prod-blue" ? local.green_testnet_lb : local.blue_testnet_lb
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

  lifecycle {
    precondition {
      condition     = local.live_mainnet_lb != local.placeholder_lb
      error_message = "Live mainnet colour ${var.live_color_mainnet} has no load balancer in remote state; refusing to point live DNS at a placeholder."
    }
  }
}

resource "aws_route53_record" "testnet_live" {
  zone_id = aws_route53_zone.veworld_public_zone[0].zone_id
  name    = "testnet.live.${local.env.environment}.${local.env.application}.${local.env.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [local.live_testnet_lb]

  lifecycle {
    precondition {
      condition     = local.live_testnet_lb != local.placeholder_lb
      error_message = "Live testnet colour ${var.live_color_testnet} has no load balancer in remote state; refusing to point live DNS at a placeholder."
    }
  }
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
