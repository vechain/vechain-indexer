# CloudFront Environment Configurations

This directory contains environment-specific configuration files for the CloudFront infrastructure deployment.

## Directory Structure

```
environments/
├── prod.yml          # Production environment configuration
├── staging.yml       # Staging environment configuration
└── README.md        # This documentation file
```

## Configuration Files

### `prod.yml`
Contains production-specific values for:
- Production domain names and certificates
- Cache policies with production-appropriate TTL values
- WAF configuration optimized for production traffic
- Production CNAMEs and origins

### `staging.yml`
Contains staging-specific values with:
- Staging domain names and certificates (update certificate ARNs)
- Shorter TTL values for faster testing iterations
- More relaxed WAF settings with logging enabled
- Staging-specific CNAMEs and origins

## Usage

These YAML files serve as documentation and reference for environment-specific values. You can use them in several ways:

### 1. Manual Terraform Variables
Copy values from the YAML files into your `terraform.tfvars` files:

```hcl
# terraform.tfvars
environment = "prod"
project_name = "veworld"
mainnet_origin_domain = "mainnet.live.prod.veworld.vechain.org"
# ... other variables
```

### 2. Direct YAML Integration
Each environment folder has a simple `environment.tf` file that reads the YAML directly:

```hcl
# In prod/environment.tf
locals {
  env = yamldecode(file("../environments/prod.yml"))
}

# In staging/environment.tf  
locals {
  env = yamldecode(file("../environments/staging.yml"))
}

# Access nested values in cloudfront.tf like:
# local.env.cache_policies.default_cache_policy_name
# local.env.waf.waf_rate_limit
```

### 3. Environment Validation
Compare your current Terraform configuration against these reference files to ensure consistency.

## Key Differences Between Environments

| Configuration | Production | Staging |
|---------------|------------|---------|
| TTL Values | 60-300 seconds | 30-60 seconds |
| WAF Rate Limit | 5,000 req/5min | 10,000 req/5min |
| WAF Logging | Disabled | Enabled |
| Log Retention | 30 days | 7 days |
| Domain Names | `.prod.` | `.staging.` |
| CNAMEs | `indexer.*` | `indexer-staging.*` |

## Updating Configurations

1. **For Staging**: Update certificate ARNs in `staging.yml` with actual staging certificates
2. **For New Environments**: Copy and modify an existing YAML file
3. **For Changes**: Update both the YAML file and the corresponding `variables.tf` defaults

## Cache Behavior Structure

Each cache behavior is defined once and automatically applied to both mainnet and testnet distributions:

```yaml
cache_behaviors:
  - name: "1-hour"                       # Applied to both mainnet and testnet
    path_pattern: "/api/v1/stargate/nft-holders/historic/1-hour"
    cache_policy_name: "hourly"         # References cache policy module
    headers_policy_name: "default"      # References headers policy module
    allowed_methods:
      - "GET"
      - "HEAD"
      - "OPTIONS"
      - "PUT"
      - "POST" 
      - "PATCH"
      - "DELETE"
    cached_methods:
      - "GET"
      - "HEAD"
    viewer_protocol_policy: "redirect-to-https"
  
  - name: "1-day"                        # Another behavior for both networks
    path_pattern: "/api/v1/stargate/nft-holders/historic/1-day"
    # ... configuration
```

**Benefits:**
- **No Duplication**: Single behavior definition applies to both mainnet and testnet
- **Easier Maintenance**: Update once, changes apply to both networks
- **Complete Configuration**: All behavior parameters in one place
- **Environment Specific**: Different behaviors per environment
- **Policy References**: Use friendly names that map to cache policy modules
- **Cleaner YAML**: 50% fewer behavior definitions to manage

## Cache Policy Structure

Each cache policy is self-contained in a list for deterministic Terraform changes:

```yaml
cache_policies:
  - id: "default"                        # Unique identifier for policy references
    name: "veworld_default_cache_policy"
    headers_policy_name: "veworld_default_header_policy"
    create_header_policy: true
    default_ttl_seconds: 60
    max_ttl_seconds: 60
    min_ttl_seconds: 60
  
  - id: "hourly"                         # Referenced by behaviors as cache_policy_name
    name: "veworld_hourly_cache_policy"
    create_header_policy: false
    default_ttl_seconds: 300
    max_ttl_seconds: 300
    min_ttl_seconds: 300
```

**Policy Benefits:**
- **Self-Contained**: All policy parameters (name, TTL, headers) in one place
- **Easy TTL Changes**: Modify cache duration directly in YAML
- **Header Policy Control**: Enable/disable header policies per cache policy
- **Deterministic Changes**: List-based iteration ensures consistent Terraform plan/apply order
- **Better Performance**: Lists are faster to iterate than maps in Terraform

## List-Based Architecture Benefits

This configuration uses lists instead of maps for both cache policies and behaviors, providing:

- **Deterministic Terraform Changes**: Order is preserved, making plans and applies consistent
- **Faster Performance**: List iteration is more efficient than map iteration in Terraform
- **Predictable Resource Names**: Resources are created with consistent, ordered naming
- **Easier Debugging**: Clear ordering makes it easier to troubleshoot configuration issues
- **Better Git Diffs**: Changes show up cleanly in version control without reordering

## Notes

- **Certificate ARNs**: The staging certificate ARNs are placeholders and need to be updated
- **Domain Names**: Adjust domain patterns based on your actual infrastructure
- **WAF Rules**: Both environments use the same managed rules but with different settings
- **Module Sources**: All environments currently use the same module sources with `cloudfront-changes` ref
- **Cache Behaviors**: Each behavior is completely configurable via YAML without touching Terraform code
- **Cache Policies**: Each policy is self-contained and dynamically created from YAML configuration

## Terraform Integration

These files work alongside your Terraform configuration structure (no workspaces needed):

```
terraform/cloudfront/
├── prod/
│   ├── environment.tf    # Simple: env = yamldecode(file("../environments/prod.yml"))
│   ├── cloudfront.tf    # Uses local.env.* (nested YAML structure)
│   ├── variables.tf     # Variable definitions (kept for overrides)
│   └── provider.tf      # AWS provider configuration
├── staging/
│   ├── environment.tf   # Simple: env = yamldecode(file("../environments/staging.yml"))
│   ├── cloudfront.tf   # Uses local.env.* (nested YAML structure)
│   ├── variables.tf    # Variable definitions (kept for overrides)
│   └── provider.tf     # AWS provider configuration
└── environments/
    ├── prod.yml        # Production configuration (nested YAML)
    └── staging.yml     # Staging configuration (nested YAML)
```

Each environment folder has a simple `environment.tf` that loads the YAML directly without any intermediate processing. The nested YAML structure is accessed using `local.env.cache_policies.*`, `local.env.waf.*`, etc.
