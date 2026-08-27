resource "aws_s3_bucket" "lb_logs" {
  bucket        = "${var.env}-${var.project}-${var.app_name}-ecs-lb-bucket"
  force_destroy = "true"
}

resource "aws_s3_bucket_policy" "lb_logs-policy" {
  bucket = aws_s3_bucket.lb_logs.bucket

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : "${data.aws_elb_service_account.default.arn}"
        },
        "Action" : "s3:PutObject",
        "Resource" : "arn:aws:s3:::${var.env}-${var.project}-${var.app_name}-ecs-lb-bucket/${var.project}-lb/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
      }
    ]
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "lb_logs" {
  bucket = aws_s3_bucket.lb_logs.bucket

  rule {
    id     = "lb-logs"
    status = "Enabled"

    expiration {
      # 6 months
      days = 183
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "lb_logs-encryption" {
  bucket = aws_s3_bucket.lb_logs.bucket

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

#Adds an ACL to bucket
resource "aws_s3_bucket_acl" "lb_logs_acl" {
  bucket     = aws_s3_bucket.lb_logs.bucket
  acl        = "private"
  depends_on = [aws_s3_bucket_ownership_controls.lb_logs_acl_ownership]

}

#Block Public Access
resource "aws_s3_bucket_public_access_block" "lb_logs_public_block" {
  bucket = aws_s3_bucket.lb_logs.bucket

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "lb_logs_acl_ownership" {
  bucket = aws_s3_bucket.lb_logs.bucket
  rule {
    object_ownership = "ObjectWriter"
  }
}
resource "aws_alb" "alb" {
  name                       = length("${var.env}-${var.app_name}-${var.project}-alb") > 32 ? "${substr("${var.env}-${var.app_name}-${var.project}-alb", 0, 30)}-x" : "${var.env}-${var.app_name}-${var.project}-alb"
  internal                   = var.internal_alb
  load_balancer_type         = var.load_balancer_type
  subnets                    = var.lb_subnets
  security_groups            = var.alb_sg
  enable_deletion_protection = var.enable_deletion_protection
  idle_timeout               = var.idle_timeout
  client_keep_alive          = var.client_keep_alive
  #enforce_security_group_inbound_rules_on_private_link_traffic only if the load balancer type is network
  enforce_security_group_inbound_rules_on_private_link_traffic = var.load_balancer_type == "network" ? "off" : null

  dynamic "access_logs" {
    for_each = var.load_balancer_type == "application" ? [1] : []
    content {
      bucket  = aws_s3_bucket.lb_logs.bucket
      prefix  = "${var.project}-lb"
      enabled = true
    }
  }

  tags = merge(
    {
      Env         = var.env
      Project     = var.project
      Application = var.app_name
    },
    can(var.network) && var.network != "" ? { Network = var.network } : {}
  )
}

# alb listener for network lb
resource "aws_lb_listener" "tcp" {
  count = var.load_balancer_type == "network" ? 1 : 0

  load_balancer_arn = aws_alb.alb.id
  port              = 80
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.nlb_target_group_tcp[0].arn
  }

}
#Create the alb listener for the load balancer
resource "aws_alb_listener" "alb_listener" {
  count = var.load_balancer_type == "application" ? 1 : 0

  load_balancer_arn = aws_alb.alb.id
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = var.internal_alb ? [1] : []
    content {
      type = var.default_action
      dynamic "fixed_response" {
        for_each = var.default_action == "fixed-response" ? [1] : []
        content {
          content_type = var.content_type
          message_body = var.message_body
          status_code  = var.status_code
        }
      }
      target_group_arn = var.default_action != "fixed-response" ? aws_alb_target_group.alb_target_group_https[0].arn : null
    }
  }

  dynamic "default_action" {
    for_each = var.internal_alb ? [] : [1]
    content {
      type = "redirect"

      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_alb_listener" "alb_listener_https" {
  count = var.load_balancer_type == "application" ? 1 : 0

  load_balancer_arn = aws_alb.alb.id
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = var.ssl_policy
  #certificate_arn = coalesce(aws_acm_certificate.nscert[0].arn, var.certificate_arn)
  certificate_arn = var.replace_cert ? aws_acm_certificate.nscert[0].arn : var.certificate_arn

  default_action {
    #type             = "forward"
    type = var.default_action
    dynamic "fixed_response" {
      for_each = var.default_action == "fixed-response" ? [1] : []
      content {
        content_type = var.content_type
        message_body = var.message_body
        status_code  = var.status_code
      }
    }
    target_group_arn = var.default_action != "fixed-response" ? aws_alb_target_group.alb_target_group_https[0].arn : null
  }

}

resource "aws_alb_target_group" "nlb_target_group_tcp" {
  count = var.load_balancer_type == "network" ? 1 : 0

  name        = length("${var.env}-${var.app_name}-${var.project}-lb-tg") >= 32 ? "${substr("${var.env}-${var.app_name}-${var.project}-lb-tg", 0, 30)}-x" : "${var.env}-${var.app_name}-${var.project}-lb-tg"
  port        = 80
  protocol    = "TCP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    protocol            = "HTTP"
    interval            = 5
    path                = "/health-check"
    healthy_threshold   = 2
    unhealthy_threshold = 10
    timeout             = 5
  }
}

resource "aws_alb_target_group" "alb_target_group_https" {
  count = var.load_balancer_type == "application" ? 1 : 0

  name        = length("${var.env}-${var.app_name}-${var.project}-lb-tg") >= 32 ? "${substr("${var.env}-${var.app_name}-${var.project}-lb-tg", 0, 30)}-x" : "${var.env}-${var.app_name}-${var.project}-lb-tg"
  port        = var.https_tg_port
  protocol    = var.is_https ? "HTTPS" : "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    healthy_threshold   = 3
    interval            = var.https_tg_healthcheck_interval
    port                = var.https_tg_healthcheck_port
    path                = var.https_tg_healthcheck_path
    protocol            = var.is_https ? "HTTPS" : "HTTP"
    unhealthy_threshold = 3
    timeout             = var.https_tg_healthcheck_timeout
  }
  tags = {
    Env     = var.env
    Project = var.project
  }
}



resource "aws_alb_target_group" "tg_1" {
  count       = var.is_tg_1_required && var.load_balancer_type == "application" ? 1 : 0
  name        = "${var.env}-${var.app_name}-${var.project}-${var.tg_1_name}"
  port        = var.tg_1_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    healthy_threshold   = 5
    interval            = 30
    port                = var.tg_1_healthcheck_port
    path                = var.tg_1_healthcheck_path
    protocol            = "HTTP"
    unhealthy_threshold = 3
  }
  tags = {
    Env     = var.env
    Project = var.project
  }
}


resource "aws_alb_target_group" "tg_2" {
  count       = var.is_tg_2_required && var.load_balancer_type == "application" ? 1 : 0
  name        = "${var.env}-${var.app_name}-${var.project}-${var.tg_2_name}"
  port        = var.tg_2_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    healthy_threshold   = 5
    interval            = 30
    port                = var.tg_2_healthcheck_port
    path                = var.tg_2_healthcheck_path
    protocol            = "HTTP"
    unhealthy_threshold = 3
  }
  tags = {
    Env     = var.env
    Project = var.project
  }
}
locals {
  rule_0_requires_header = var.rule_0_required_header_name != ""

  # A rule caps at 5 condition values, one spent per header value, so paths spill over.
  rule_0_chunks = chunklist(var.rule_0_path_pattern == null ? [] : var.rule_0_path_pattern, 5 - length(var.rule_0_required_header_values))

  # Rule 0 can span several rules, so the fixed priorities below start after it.
  rule_0_span = max(1, length(local.rule_0_chunks))
}

resource "aws_alb_listener_rule" "listener_rule" {
  count = var.is_rule_0_required && var.load_balancer_type == "application" ? length(local.rule_0_chunks) : 0

  lifecycle {
    precondition {
      condition     = (var.rule_0_required_header_name == "") == (length(var.rule_0_required_header_values) == 0)
      error_message = "rule_0_required_header_name and rule_0_required_header_values must both be set or both be empty."
    }
  }

  priority     = count.index + 1
  listener_arn = aws_alb_listener.alb_listener_https[0].arn
  action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.alb_target_group_https[0].arn
  }
  condition {
    path_pattern {
      values = local.rule_0_chunks[count.index]
    }
  }

  dynamic "condition" {
    for_each = local.rule_0_requires_header ? [1] : []
    content {
      http_header {
        http_header_name = var.rule_0_required_header_name
        values           = var.rule_0_required_header_values
      }
    }
  }
}

resource "aws_alb_listener_rule" "listener_rule_1" {

  priority = local.rule_0_span + 1
  count    = var.is_rule_1_required && var.load_balancer_type == "application" ? 1 : 0

  listener_arn = aws_alb_listener.alb_listener_https[0].arn

  condition {
    path_pattern {
      values = var.rule_1_path_pattern
    }
  }

  action {
    type = "authenticate-oidc"

    authenticate_oidc {
      authorization_endpoint     = "${var.okta_auth_server_base_url}/oauth2/v1/authorize"
      client_id                  = jsondecode(data.aws_secretsmanager_secret_version.okta_ipfs[0].secret_string)["okta_client_id"]
      client_secret              = jsondecode(data.aws_secretsmanager_secret_version.okta_ipfs[0].secret_string)["okta_client_secret"]
      issuer                     = var.okta_auth_server_base_url
      token_endpoint             = "${var.okta_auth_server_base_url}/oauth2/v1/token"
      user_info_endpoint         = "${var.okta_auth_server_base_url}/oauth2/v1/userinfo"
      on_unauthenticated_request = "authenticate"
      scope                      = "openid"
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.tg_1[0].arn
  }

}

data "aws_secretsmanager_secret_version" "okta_ipfs" {
  count     = var.is_rule_2_required && var.load_balancer_type == "application" ? 1 : 0
  secret_id = var.secret_id
}

resource "aws_alb_listener_rule" "listener_rule_2" {

  count        = var.is_rule_2_required && var.load_balancer_type == "application" ? 1 : 0
  listener_arn = aws_alb_listener.alb_listener_https[0].arn
  priority     = local.rule_0_span + 2
  condition {
    path_pattern {
      values = var.rule_2_path_pattern
    }
  }

  action {
    type = "authenticate-oidc"

    authenticate_oidc {
      authorization_endpoint = "${var.okta_auth_server_base_url}/oauth2/v1/authorize"
      client_id              = jsondecode(data.aws_secretsmanager_secret_version.okta_ipfs[0].secret_string)["okta_client_id"]
      client_secret          = jsondecode(data.aws_secretsmanager_secret_version.okta_ipfs[0].secret_string)["okta_client_secret"]

      issuer                     = var.okta_auth_server_base_url
      token_endpoint             = "${var.okta_auth_server_base_url}/oauth2/v1/token"
      user_info_endpoint         = "${var.okta_auth_server_base_url}/oauth2/v1/userinfo"
      on_unauthenticated_request = "authenticate"
      scope                      = "openid"
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.tg_2[0].arn
  }
}


resource "aws_alb_listener_rule" "listener_rule_3" {

  priority = local.rule_0_span + 3
  count    = var.is_rule_3_required && var.load_balancer_type == "application" ? 1 : 0

  listener_arn = aws_alb_listener.alb_listener_https[0].arn

  condition {
    path_pattern {
      values = var.rule_3_path_pattern
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.tg_1[0].arn
  }

}

resource "aws_alb_listener_rule" "listener_rule_4" {
  count        = var.is_rule_4_required && var.load_balancer_type == "application" ? 1 : 0
  priority     = local.rule_0_span + 4
  listener_arn = aws_alb_listener.alb_listener_https[0].arn
  action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.alb_target_group_https[0].arn
  }
  condition {
    path_pattern {
      values = ["/*"]
    }
  }
}



output "tg1_arn" {
  value = var.is_tg_1_required ? aws_alb_target_group.tg_1[0].arn : null
}

output "tg2_arn" {
  value = var.is_tg_2_required ? aws_alb_target_group.tg_2[0].arn : null
}
