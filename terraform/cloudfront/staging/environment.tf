# Pull in staging environment settings from YAML configuration file
# This file reads ../environments/staging.yml directly (no workspace dependency)
# Values can then be used as local.env.<nested_key> in other files
# 
# Note: Module sources cannot be dynamic - they must be static strings due to Terraform limitations
# Therefore, module sources are hardcoded in the .tf files and cannot be configured via YAML

locals {
  # Load the staging environment configuration directly from YAML
  # Access nested values using local.env.cache_policies.*, local.env.waf.*, etc.
  env = yamldecode(file("../environments/staging.yml"))
  
  # Cache policy mapping from shared remote state
  # Header and origin request policies are now defined directly in behaviors and default configs
  cache_policy_map = data.terraform_remote_state.shared.outputs.cache_policy_map
  
  # Cache behaviors are now shared between mainnet and testnet
  # No filtering needed since behaviors apply to both networks
}
