# AMP + AMG workspaces for the veworld-indexer observability migration
# (see notes/observability-migration.md — phase 2). Ships intentionally
# empty: no dashboards, no scrape targets, no alert rules. Downstream
# stacks (indexer / API sidecars, dashboards-as-code) reference the
# outputs of this stack via terraform_remote_state.
#
# AMG `permission_type = "SERVICE_MANAGED"` is a console-only value —
# the CreateWorkspace API (which terraform uses) rejects it with
# "a Workspace Role ARN should be provided". So the workspace runs
# CUSTOMER_MANAGED against a role we own that attaches the same two
# AWS-managed policies the console flow would have attached.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# Only prod is defined today; guard against `default` workspace mistakes.
resource "terraform_data" "workspace_guard" {
  lifecycle {
    precondition {
      condition     = contains(["prod"], terraform.workspace)
      error_message = "Use workspace 'prod' only (not default). Example: terraform workspace select -or-create prod"
    }
  }
}

# ---------------------------------------------------------------------------
# Amazon Managed Prometheus
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "amp" {
  name              = "/aws/prometheus/${local.name_prefix}"
  retention_in_days = local.env.log_retention_days
}

resource "aws_prometheus_workspace" "this" {
  alias = local.name_prefix

  logging_configuration {
    log_group_arn = "${aws_cloudwatch_log_group.amp.arn}:*"
  }
}

# ---------------------------------------------------------------------------
# Amazon Managed Grafana
# ---------------------------------------------------------------------------

# Workspace role assumed by the AMG service to read data sources. This
# mirrors what `permission_type = "SERVICE_MANAGED"` would attach in the
# console flow: read scopes for AMP (PROMETHEUS) and CloudWatch (metrics
# + logs). When we add a new datasource, attach the matching read policy
# here.
#
# Trust is scoped to grafana.amazonaws.com AND restricted to AMG
# workspaces in this account/region via aws:SourceAccount + aws:SourceArn.
# Without those a workspace in any other account that targets this ARN
# could in principle assume the role (confused-deputy hardening). The
# ArnLike wildcard is needed because the workspace ID isn't known at
# role-create time and the workspace depends on the role.
resource "aws_iam_role" "grafana_workspace" {
  name = "${local.name_prefix}-grafana-workspace"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "grafana.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = {
          "aws:SourceAccount" = data.aws_caller_identity.current.account_id
        }
        ArnLike = {
          "aws:SourceArn" = "arn:aws:grafana:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:/workspaces/*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "grafana_cloudwatch" {
  role = aws_iam_role.grafana_workspace.name
  # `AmazonGrafanaCloudWatchAccess` is not an AWS-managed policy — the
  # name is a common assumption (mirror of `AmazonPrometheusQueryAccess`)
  # but IAM returns NoSuchEntity for that ARN. AMG's own docs build the
  # CloudWatch data-source role from the generic read policy instead.
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchReadOnlyAccess"
}

resource "aws_iam_role_policy_attachment" "grafana_prometheus" {
  role       = aws_iam_role.grafana_workspace.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonPrometheusQueryAccess"
}

# AmazonPrometheusQueryAccess doesn't cover rules / alerts / silences.
# These extra actions let Grafana surface AMP rules + state under
# Alerting → Alert rules and create/clear silences from the UI. Rules
# themselves stay terraform-managed (Grafana's external-rules UI is
# read-only).
data "aws_iam_policy_document" "grafana_amp_alerts" {
  statement {
    sid    = "AMPRulesAndAlertmanager"
    effect = "Allow"
    actions = [
      "aps:ListRules",
      "aps:ListAlerts",
      "aps:GetAlertManagerStatus",
      "aps:GetAlertManagerSilence",
      "aps:ListAlertManagerSilences",
      "aps:PutAlertManagerSilences",
      "aps:DeleteAlertManagerSilence",
      "aps:GetAlertManagerReceivers",
    ]
    resources = [aws_prometheus_workspace.this.arn]
  }
}

resource "aws_iam_role_policy" "grafana_amp_alerts" {
  name   = "${local.name_prefix}-grafana-amp-alerts"
  role   = aws_iam_role.grafana_workspace.id
  policy = data.aws_iam_policy_document.grafana_amp_alerts.json
}

resource "aws_grafana_workspace" "this" {
  name                     = local.name_prefix
  description              = "${var.project} ${terraform.workspace} observability workspace"
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["SAML"]
  permission_type          = "CUSTOMER_MANAGED"
  role_arn                 = aws_iam_role.grafana_workspace.arn
  # AMG only creates workspaces at Grafana 8/9/10/12 — the v11 release
  # train was folded into v12, so "11.0" hard-fails CreateWorkspace with
  # a ValidationException. v12.x includes the v11 features we want
  # (dashboards-as-code provider improvements).
  grafana_version = "12.4"
}

# Gated on the Okta IdP metadata URL being populated. While empty, the
# workspace exists but no one can SSO. Service-account token minted
# below still works because tokens are independent of the SAML provider.
resource "aws_grafana_workspace_saml_configuration" "okta" {
  count = local.env.okta_saml_metadata_url == "" ? 0 : 1

  workspace_id       = aws_grafana_workspace.this.id
  idp_metadata_url   = local.env.okta_saml_metadata_url
  admin_role_values  = local.env.grafana_admin_okta_groups
  editor_role_values = local.env.grafana_editor_okta_groups

  # Map the SAML assertion attribute names emitted by the Okta "Amazon
  # Managed Grafana" catalog app to Grafana's identity fields. The catalog
  # app sends `displayName` (name) and `mail` (email + login) by default;
  # `role` is the group attribute statement added to the app for
  # admin/editor mapping. Names must match the app exactly or role mapping
  # never fires and every SSO user lands as Viewer.
  login_assertion = "mail"
  email_assertion = "mail"
  name_assertion  = "displayName"
  role_assertion  = "role"
}

# ---------------------------------------------------------------------------
# Service account for the Grafana terraform provider
# ---------------------------------------------------------------------------
#
# AMG caps service-account-token TTLs at 30 days regardless of who mints
# them, so the token is rotated by `time_rotating` ahead of expiry. The
# token name carries the rotation timestamp so replacements are visible
# in the AMG console.

resource "time_rotating" "grafana_sa_token" {
  rotation_days = 25
}

resource "aws_grafana_workspace_service_account" "terraform" {
  name         = "terraform"
  grafana_role = "ADMIN"
  workspace_id = aws_grafana_workspace.this.id
}

resource "aws_grafana_workspace_service_account_token" "terraform" {
  name               = "terraform-${formatdate("YYYYMMDD", time_rotating.grafana_sa_token.rotation_rfc3339)}"
  service_account_id = aws_grafana_workspace_service_account.terraform.service_account_id
  workspace_id       = aws_grafana_workspace.this.id
  seconds_to_live    = 60 * 60 * 24 * 30 # 30 days, the AMG maximum

  lifecycle {
    create_before_destroy = true
    replace_triggered_by  = [time_rotating.grafana_sa_token.rotation_rfc3339]
  }
}

# ---------------------------------------------------------------------------
# Secrets Manager pass-through for the SA token
# ---------------------------------------------------------------------------
#
# Cross-stack convention: secrets cross module boundaries as a Secrets
# Manager ARN, not as a literal value. Downstream stacks (e.g. dashboards
# provisioning) read the ARN from this stack's outputs and resolve the
# value through a Secrets Manager data source at apply time.

resource "aws_secretsmanager_secret" "amg_sa_token" {
  name                    = "${local.name_prefix}-amg-sa-token"
  description             = "Grafana service-account token used by the observability dashboards terraform provider. Rotated by time_rotating."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "amg_sa_token" {
  secret_id     = aws_secretsmanager_secret.amg_sa_token.id
  secret_string = aws_grafana_workspace_service_account_token.terraform.key

  lifecycle {
    create_before_destroy = true
  }
}
