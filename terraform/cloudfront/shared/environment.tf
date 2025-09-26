# Pull in shared environment settings from YAML configuration file
# This file reads ../environments/shared.yml directly

locals {
  # Load the shared environment configuration directly from YAML
  env = yamldecode(file("../environments/shared.yml"))
}
