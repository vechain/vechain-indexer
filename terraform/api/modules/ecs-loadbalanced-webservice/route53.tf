resource "aws_route53_zone" "private" {
  count = var.private_zone_name != "" ? 1 : 0
  name  = var.private_zone_name
  vpc {
    vpc_id = var.vpc_id
  }

  lifecycle {
    ignore_changes = [vpc]
  }
  tags = {
    Name        = lower("${var.private_zone_name}")
    Environment = var.env
    Project     = var.project
    Terraform   = "true"
  }
}


resource "aws_route53_record" "private_domain-ns" {
  count   = var.private_zone_name != "" && var.private_zone_record_name != "" ? 1 : 0
  zone_id = aws_route53_zone.private[0].zone_id
  name    = var.private_zone_record_name
  type    = var.subdomain_type
  ttl     = var.ttl
  records = var.records
}


resource "aws_route53_record" "public_zone" {
  count = var.public_zone_name != "" ? 1 : 0

  name    = var.public_zone_record_name
  type    = "A"
  zone_id = aws_route53_zone.public[count.index].zone_id

  alias {
    evaluate_target_health = true
    name                   = aws_alb.alb.dns_name
    zone_id                = aws_alb.alb.zone_id
  }
}

resource "aws_route53_zone" "public" {
  count = var.public_zone_name != "" ? 1 : 0
  name  = var.public_zone_name

  tags = {
    Name        = lower("${var.public_zone_name}")
    Environment = var.env
    Project     = var.project
    Terraform   = "true"
  }
}


resource "aws_acm_certificate" "nscert" {
  count             = var.public_zone_name != "" && var.create_cert ? 1 : 0
  domain_name       = var.env == "prod" ? "${var.domain_name}" : "${var.env}${var.domain_name}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
  tags = {
    Environment = "${var.project}-${var.env}"
  }
}
resource "aws_route53_record" "nscertvalidators" {
  count = var.public_zone_name != "" ? length(tolist(aws_acm_certificate.nscert[0].domain_validation_options)) : 0

  allow_overwrite = true
  name            = tolist(aws_acm_certificate.nscert[0].domain_validation_options)[count.index].resource_record_name
  records         = [tolist(aws_acm_certificate.nscert[0].domain_validation_options)[count.index].resource_record_value]
  ttl             = 60
  type            = tolist(aws_acm_certificate.nscert[0].domain_validation_options)[count.index].resource_record_type
  zone_id         = aws_route53_zone.public[0].zone_id
  depends_on      = [aws_acm_certificate.nscert]
}