locals {
  env = merge(yamldecode(file("environments/${terraform.workspace}.yml")))
}
