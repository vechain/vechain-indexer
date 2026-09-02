# Certificate and alias records for the dead-colour distributions.
# Only the `dead` workspace creates these; prod and staging carry pre-existing
# certificate ARNs in their environment files instead.

locals {
  # Other workspaces zero out the resources below, but `terraform validate` still
  # schema-checks them, so every fallback here has to be well-formed rather than
  # empty. None of these values reach AWS.
  dead_zone_id      = try(local.env_config.hosted_zone_id, "ZUNUSED")
  dead_mainnet_host = local.is_dead ? local.env_config.mainnet_cnames[0] : "unused.invalid"
  dead_testnet_host = local.is_dead ? local.env_config.testnet_cnames[0] : "unused.invalid"

  # Hosted zone CloudFront alias targets always live in, not our own zone.
  cloudfront_hosted_zone_id = "Z2FDTNDATAQYW2"

  dead_alias_records = local.is_dead ? {
    "mainnet-A"    = { host = local.dead_mainnet_host, type = "A", target = module.mainnet_cloudfront[0].domain_name }
    "mainnet-AAAA" = { host = local.dead_mainnet_host, type = "AAAA", target = module.mainnet_cloudfront[0].domain_name }
    "testnet-A"    = { host = local.dead_testnet_host, type = "A", target = module.testnet_cloudfront[0].domain_name }
    "testnet-AAAA" = { host = local.dead_testnet_host, type = "AAAA", target = module.testnet_cloudfront[0].domain_name }
  } : {}
}

# One certificate covers both networks; CloudFront requires it in us-east-1.
resource "aws_acm_certificate" "dead" {
  count    = local.is_dead ? 1 : 0
  provider = aws.us_east_1

  domain_name               = local.dead_mainnet_host
  subject_alternative_names = [local.dead_testnet_host]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "dead_cert_validation" {
  for_each = {
    for option in try(aws_acm_certificate.dead[0].domain_validation_options, []) :
    option.domain_name => option
  }

  zone_id         = local.dead_zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  records         = [each.value.resource_record_value]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "dead" {
  count    = local.is_dead ? 1 : 0
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.dead[0].arn
  validation_record_fqdns = [for record in aws_route53_record.dead_cert_validation : record.fqdn]
}

resource "aws_route53_record" "dead_alias" {
  for_each = local.dead_alias_records

  zone_id = local.dead_zone_id
  name    = each.value.host
  type    = each.value.type

  alias {
    name                   = each.value.target
    zone_id                = local.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}
