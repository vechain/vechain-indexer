resource "aws_route53_record" "devnet" {
  zone_id = local.env.zone_id
  name    = "${local.env.environment}.devnet.veworld.vechain.org"
  type    = "CNAME"
  ttl     = 300
  records = [module.ecs-lb-service-api["dev"].alb_dns_name]
}