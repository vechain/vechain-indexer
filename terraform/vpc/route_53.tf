variable "live_color" {
  type        = string
  description = "The environment that will receive live traffic. Allowed values are 'prod-blue' or 'prod-green'. This variable is required."

  validation {
    condition     = var.live_color == "prod-blue" || var.live_color == "prod-green"
    error_message = "Invalid value for live_color. Allowed values are 'prod-blue' or 'prod-green', e.g. `terraform apply --var=live_color=prod-blue`"
  }

  default = "unspecified"
}
variable "testnet_only" {
  type        = bool
  description = "If true, only the testnet records will be created. If false, both mainnet and testnet records will be created. This variable is optional."
   validation {
    condition     = var.testnet_only == true || var.testnet_only == false
    error_message = "value for testnet_only must be a boolean, e.g. `terraform apply --var=testnet_only=true`"
  }
}

locals {
  live_mainnet_lb    = var.live_color == "prod-blue" && var.testnet_only == false ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet : (var.live_color != "prod-blue" && var.testnet_only == false ? data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet : (var.live_color == "prod-blue" && var.testnet_only == true ? data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet : (var.live_color != "prod-blue" && var.testnet_only == true ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet : "place.holder.domain")))
  live_testnet_lb    = var.live_color == "prod-blue" ? data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet : data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet
  # If the dead environment has been torn down, dead records will be nullified
  dead_mainnet_lb    = var.live_color == "prod-blue" && var.testnet_only == true ? try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet, "place.holder.domain") : (var.live_color != "prod-blue" && var.testnet_only == true ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet, "place.holder.domain") : (var.live_color == "prod-blue" && var.testnet_only == false ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_mainnet, "place.holder.domain") : (var.live_color != "prod-blue" && var.testnet_only == false ? try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_mainnet, "place.holder.domain") : "place.holder.domain")))
  dead_testnet_lb    = var.live_color == "prod-blue" ? try(data.terraform_remote_state.api-green.outputs.load_balancer_domain_testnet, "place.holder.domain") : try(data.terraform_remote_state.api-blue.outputs.load_balancer_domain_testnet, "place.holder.domain")
}
output "live_mainnet_lb" {
  value = local.live_mainnet_lb
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
