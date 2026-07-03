# Loads workspace-specific settings from environments/${terraform.workspace}.yml.
# Consistent with terraform/api and terraform/vpc in this repo.
locals {
  env = merge(yamldecode(file("environments/${terraform.workspace}.yml")))
}
