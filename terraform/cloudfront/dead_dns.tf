# Certificate and alias records for the dead-colour distributions. Only the
# `dead` workspace creates these; the others carry a certificate ARN in their
# environment file and write no DNS.

locals {
  # Non-empty fallbacks: validate schema-checks these where their count is zero.
  dead_zone_id            = try(data.aws_route53_zone.dead[0].zone_id, "ZUNUSED")
  dead_zone_name          = try(local.env_config.hosted_zone_name, "unused.invalid.")
  dead_certificate_domain = try(local.env_config.certificate_domain, "unused.invalid")
  dead_mainnet_host       = local.is_dead ? local.env_config.mainnet_cnames[0] : "unused.invalid"
  dead_testnet_host       = local.is_dead ? local.env_config.testnet_cnames[0] : "unused.invalid"

  # Hosted zone CloudFront alias targets always live in, not our own zone.
  cloudfront_hosted_zone_id = "Z2FDTNDATAQYW2"

  dead_alias_records = local.is_dead ? {
    "mainnet-A"    = { host = local.dead_mainnet_host, type = "A", target = module.mainnet_cloudfront[0].domain_name }
    "mainnet-AAAA" = { host = local.dead_mainnet_host, type = "AAAA", target = module.mainnet_cloudfront[0].domain_name }
    "testnet-A"    = { host = local.dead_testnet_host, type = "A", target = module.testnet_cloudfront[0].domain_name }
    "testnet-AAAA" = { host = local.dead_testnet_host, type = "AAAA", target = module.testnet_cloudfront[0].domain_name }
  } : {}
}

data "aws_route53_zone" "dead" {
  count = local.is_dead ? 1 : 0
  name  = local.dead_zone_name
}

# Both hostnames are one label under dead.veworld, so a single wildcard covers
# them, leaving one validation record whose count is known before the apply.
# CloudFront requires the certificate in us-east-1.
resource "aws_acm_certificate" "dead" {
  count    = local.is_dead ? 1 : 0
  provider = aws.us_east_1

  domain_name       = local.dead_certificate_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "dead_cert_validation" {
  count = local.is_dead ? 1 : 0

  zone_id         = local.dead_zone_id
  name            = one(aws_acm_certificate.dead[0].domain_validation_options).resource_record_name
  type            = one(aws_acm_certificate.dead[0].domain_validation_options).resource_record_type
  records         = [one(aws_acm_certificate.dead[0].domain_validation_options).resource_record_value]
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
