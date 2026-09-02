# Alias records pointing the dead-colour hostnames at their distributions.
# Only the `dead` workspace creates these; the zones are looked up by name
# because they are managed outside this repository.

locals {
  # Non-empty fallbacks: validate schema-checks these where their count is zero.
  dead_mainnet_host = local.is_dead ? local.env_config.mainnet_cnames[0] : "unused.invalid"
  dead_testnet_host = local.is_dead ? local.env_config.testnet_cnames[0] : "unused.invalid"

  # Hosted zone CloudFront alias targets always live in, not our own zone.
  cloudfront_hosted_zone_id = "Z2FDTNDATAQYW2"

  dead_alias_records = local.is_dead ? merge([
    for net in ["mainnet", "testnet"] : {
      for type in ["A", "AAAA"] : "${net}-${type}" => {
        host   = net == "mainnet" ? local.dead_mainnet_host : local.dead_testnet_host
        zone   = try(data.aws_route53_zone.dead[net].zone_id, "")
        target = net == "mainnet" ? module.mainnet_cloudfront[0].domain_name : module.testnet_cloudfront[0].domain_name
        type   = type
      }
    }
  ]...) : {}
}

data "aws_route53_zone" "dead" {
  for_each = local.is_dead ? {
    mainnet = local.env_config.mainnet_hosted_zone_name
    testnet = local.env_config.testnet_hosted_zone_name
  } : {}

  name = each.value
}

resource "aws_route53_record" "dead_alias" {
  for_each = local.dead_alias_records

  zone_id = each.value.zone
  name    = each.value.host
  type    = each.value.type

  alias {
    name                   = each.value.target
    zone_id                = local.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}
