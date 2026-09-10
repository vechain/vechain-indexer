# The vpc stack's Route53 apply is the only definition of the live colour, so
# read it back from there instead of teaching the services which one they are.
data "terraform_remote_state" "vpc" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-${terraform.workspace}"
    key    = "workspaces/${terraform.workspace}/veworld-indexer-vpc.tfstate"
    region = "eu-west-1"
  }
}

locals {
  # Until the vpc stack publishes the outputs, alert on both colours rather than neither.
  live_colours = {
    for network in ["mainnet", "testnet"] :
    network => try(
      [trimprefix(data.terraform_remote_state.vpc.outputs["live_color_${network}"], "prod-")],
      ["blue", "green"],
    )
  }

  live_colour_rules = flatten([
    for network, colours in local.live_colours : [
      for colour in colours : {
        record = "veworld:live_colour"
        expr   = "label_replace(label_replace(vector(1), \"deployment\", \"${colour}\", \"\", \"\"), \"network\", \"${network}\", \"\", \"\")"
      }
    ]
  ])
}

# One series per network, labelled with the live colour, for alerts to join on.
resource "aws_prometheus_rule_group_namespace" "live_colour" {
  workspace_id = aws_prometheus_workspace.this.id
  name         = "${local.name_prefix}-live-colour"
  data = yamlencode({
    groups = [{
      name  = "${local.name_prefix}-live-colour"
      rules = local.live_colour_rules
    }]
  })
}
