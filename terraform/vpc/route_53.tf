variable "live_color" {
  type        = string
  description = "The environment that will receive live traffic. Allowed values are 'blue' or 'green'. This variable is required."

  validation {
    condition     = var.live_color == "blue" || var.live_color == "green"
    error_message = "Invalid value for live_color. Allowed values are 'blue' or 'green', e.g. `terraform apply --var=live_color=blue`"
  }

  default = "unspecified"
}

locals {
  live_mainnet_lb    = var.live_color == "blue" ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet : data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet
  live_testnet_lb    = var.live_color == "blue" ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet : data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet
  # If the dead environment has been torn down, dead records will be nullified
  dead_mainnet_lb    = var.live_color == "blue" ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet, "") : data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet
  dead_testnet_lb    = var.live_color == "blue" ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet, "") : data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet
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
