terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

###################################
# CloudFront Cache Policy
###################################
resource "aws_cloudfront_cache_policy" "cache_policy" {
  name        = var.cache_policy
  default_ttl = var.default_ttl
  max_ttl     = var.max_ttl
  min_ttl     = var.min_ttl


  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_gzip   = var.enable_gzip
    enable_accept_encoding_brotli = var.enable_brotli
    cookies_config {
      cookie_behavior = var.cookie_behavior
    }
    headers_config {
      header_behavior = var.header_behavior

      dynamic "headers" {
        for_each = length(var.header_items) > 0 ? [1] : []
        content {
          items = var.header_items
        }
      }
    }
    query_strings_config {
      query_string_behavior = var.query_string_behavior
    }
  }
}

###################################
# CloudFront Response Header Policy
###################################
resource "aws_cloudfront_response_headers_policy" "header_policy" {
  count = var.create_header_policy
  name  = var.headers_policy
  security_headers_config {
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "SAMEORIGIN"
      override     = true
    }
    referrer_policy {
      override        = true
      referrer_policy = "strict-origin-when-cross-origin"
    }
    strict_transport_security {
      access_control_max_age_sec = 63072000
      include_subdomains         = true
      override                   = true
      preload                    = false
    }
    xss_protection {
      mode_block = true
      override   = true
      protection = true
      report_uri = ""
    }
  }
}
