variable "live_color_mainnet" {
  type        = string
  description = "The mainnet environment that will receive live traffic. Allowed values are 'prod-blue' or 'prod-green'. This variable is required."

  validation {
    condition     = var.live_color_mainnet == "prod-blue" || var.live_color_mainnet == "prod-green"
    error_message = "Invalid value for live_color_mainnet. Allowed values are 'prod-blue' or 'prod-green', e.g. `terraform apply --var=live_color_mainnet=prod-blue`"
  }

  default = "unspecified"
}

variable "live_color_testnet" {
  type        = string
  description = "The testnet environment that will receive live traffic. Allowed values are 'prod-blue' or 'prod-green'. This variable is required."

  validation {
    condition     = var.live_color_testnet == "prod-blue" || var.live_color_testnet == "prod-green"
    error_message = "Invalid value for live_color_testnet. Allowed values are 'prod-blue' or 'prod-green', e.g. `terraform apply --var=live_color_testnet=prod-blue`"
  }

  default = "unspecified"
}
variable "network" {
  type        = string
  description = "The network that will receive live traffic. Allowed values are 'mainnet' or 'testnet'. This variable is required."
  validation {
    condition     = var.network == "mainnet" || var.network == "testnet" || var.network == "all"
    error_message = "Invalid value for network. Allowed values are 'mainnet', 'testnet', or 'all', e.g. `terraform apply --var=network=mainnet`"
  }
  default = "all"
}

locals {
  # Determine which networks should be switched in this run.
  update_mainnet = var.network == "mainnet" || var.network == "all"
  update_testnet = var.network == "testnet" || var.network == "all"

  # Helper – opposite colour of the value provided (current live colour).
  opposite_color_mainnet = var.live_color_mainnet == "prod-blue" ? "prod-green" : "prod-blue"
  opposite_color_testnet = var.live_color_testnet == "prod-blue" ? "prod-green" : "prod-blue"

  # Mainnet – choose LB based on whether we are switching this network.
  live_mainnet_lb = local.update_mainnet ? (
    var.live_color_mainnet == "prod-blue" ? data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet : data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet
  ) : (
    var.live_color_mainnet == "prod-blue" ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet : data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet
  )

  dead_mainnet_lb = local.update_mainnet ? (
    var.live_color_mainnet == "prod-blue" ? try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet,  "place.holder.domain") : try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet, "place.holder.domain")
  ) : (
    var.live_color_mainnet == "prod-blue" ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet, "place.holder.domain") : try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet,  "place.holder.domain")
  )

  # Testnet – analogous logic.
  live_testnet_lb = local.update_testnet ? (
    var.live_color_testnet == "prod-blue" ? data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet : data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet
  ) : (
    var.live_color_testnet == "prod-blue" ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet : data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet
  )

  dead_testnet_lb = local.update_testnet ? (
    var.live_color_testnet == "prod-blue" ? try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet,  "place.holder.domain") : try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet, "place.holder.domain")
  ) : (
    var.live_color_testnet == "prod-blue" ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet, "place.holder.domain") : try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet,  "place.holder.domain")
  )
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
