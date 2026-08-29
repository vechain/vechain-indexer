# Role shared-infra.yml assumes to plan this stack and terraform/cloudfront on a
# PR. Separate from the deploy role, which carries AdministratorAccess and is
# assumable from any ref: a plan runs Terraform the PR author controls.
resource "aws_iam_role" "github_actions_plan" {
  count       = local.env.environment == "prod" ? 1 : 0
  name        = "veworld-indexer-github-actions-prod-plan"
  description = "Read-only OIDC role for shared-infra terraform plans"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # The plan runs on pull_request; the ref entry covers a manual dispatch.
        StringLike = {
          "token.actions.githubusercontent.com:sub" = [
            "repo:vechain/vechain-indexer:pull_request",
            "repo:vechain/vechain-indexer:ref:refs/heads/main",
          ]
        }
      }
    }]
  })
}

# Covers the resource reads a plan makes, and s3:Get*/List* for remote state.
resource "aws_iam_role_policy_attachment" "github_actions_plan_read_only" {
  count      = local.env.environment == "prod" ? 1 : 0
  role       = aws_iam_role.github_actions_plan[0].name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# ReadOnlyAccess stops at Describe*/List*, so plan-time secrets are granted here.
resource "aws_iam_role_policy" "github_actions_plan_secrets" {
  count = local.env.environment == "prod" ? 1 : 0
  name  = "plan-secret-reads"
  role  = aws_iam_role.github_actions_plan[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "secretsmanager:GetSecretValue"
      Resource = [
        local.env.mongodb_secret_arn,
        "arn:aws:secretsmanager:${local.env.region}:${data.aws_caller_identity.current.account_id}:secret:/prod/veworld/cloudfront-origin-verify-token-*",
        # Tracks the secret_id built in cloudfront_waf.tf.
        "arn:aws:secretsmanager:${local.env.region}:${data.aws_caller_identity.current.account_id}:secret:/prod/${var.project}/waf-rate-limit-bypass-token-*",
      ]
    }]
  })
}
