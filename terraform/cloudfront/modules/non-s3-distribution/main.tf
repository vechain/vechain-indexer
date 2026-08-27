terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

########################################
# non S3 backed CloudFront Distribution
########################################

resource "aws_cloudfront_distribution" "non_s3_distribution" {
  comment         = var.origin_domain
  enabled         = true
  staging         = var.staging
  is_ipv6_enabled = true
  origin {
    domain_name = var.origin_domain
    origin_id   = "origin-${var.origin_domain}"
    dynamic "custom_header" {
      for_each = var.origin_custom_headers
      content {
        name  = custom_header.key
        value = custom_header.value
      }
    }
    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_keepalive_timeout = 5
      origin_protocol_policy   = "https-only"
      origin_read_timeout      = 30
      origin_ssl_protocols     = ["TLSv1.2"]
    }
  }
  aliases = var.cnames

  default_cache_behavior {
    allowed_methods            = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods             = ["GET", "HEAD", "OPTIONS"]
    target_origin_id           = "origin-${var.origin_domain}"
    compress                   = true
    viewer_protocol_policy     = "redirect-to-https"
    response_headers_policy_id = var.headers_policy_id
    cache_policy_id            = var.cache_policy_id
    origin_request_policy_id   = var.origin_request_policy_id
  }

  dynamic "ordered_cache_behavior" {
    for_each = var.ordered_cache_behaviors
    content {
      path_pattern     = ordered_cache_behavior.value.path_pattern
      target_origin_id = ordered_cache_behavior.value.target_origin_id

      allowed_methods = ordered_cache_behavior.value.allowed_methods
      cached_methods  = ordered_cache_behavior.value.cached_methods

      cache_policy_id            = ordered_cache_behavior.value.cache_policy_id
      response_headers_policy_id = ordered_cache_behavior.value.headers_policy_id
      origin_request_policy_id   = ordered_cache_behavior.value.origin_request_policy_id

      viewer_protocol_policy = ordered_cache_behavior.value.viewer_protocol_policy
    }
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  viewer_certificate {
    acm_certificate_arn      = var.certificate_arn
    minimum_protocol_version = "TLSv1.2_2021"
    ssl_support_method       = "sni-only"
  }
  web_acl_id                      = var.waf_web_acl
  continuous_deployment_policy_id = var.continuous_deployment_policy_id
}

