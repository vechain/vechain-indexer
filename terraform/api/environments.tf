# pull in workspace specific environment settings from yaml file under environments directory
# can then be used as local.env.<key> in other files
locals {
  env = merge(yamldecode(file("environments/${terraform.workspace}.yml")))
}

# Image tags are resolved per service so a release that changed only one of
# them leaves the other's task definition untouched, and no redeploy happens.
# Falls back to the net-level image_version when no override is present.
locals {
  service_image_version = {
    for net, cfg in local.env.enabled_nets : net => {
      api     = try(cfg.api.image_version, cfg.image_version)
      indexer = try(cfg.indexer.image_version, cfg.image_version)
    }
  }
}
