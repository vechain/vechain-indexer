# Shared CloudFront Resources

This module contains shared cache policies and WAF configuration that are used by both production and staging environments.

## Structure

- `main.tf` - Cache policies and WAF modules
- `provider.tf` - AWS providers and backend configuration
- `environment.tf` - Loads configuration from shared.yml
- `outputs.tf` - Exports resources for remote state consumption

## Configuration

Configuration is defined in `../environments/shared.yml` and includes:
- **Cache policies V1** (cache_policies_v1): Used by staging and production environments
- **Cache policies V2** (cache_policies_v2): Alternative cache policy configuration
- WAF configuration and managed rules
- Environment and project settings

### Cache Policy Versions

Two versions of cache policies are maintained:
- `cache_policies_v1`: Default policies used by staging and prod environments
- `cache_policies_v2`: Alternative policy configuration for future use or A/B testing

## Deployment

This module must be deployed before production and staging environments:

```bash
# Deploy shared resources first
cd shared/
terraform init
terraform apply

# Then deploy environments that depend on shared resources
cd ../prod/
terraform init
terraform apply

cd ../staging/
terraform init  
terraform apply
```

## Remote State

This module stores its state in:
- **Bucket**: `veworld-indexer-terraform-state-prod`
- **Key**: `shared/veworld-indexer-cloudfront.tfstate`
- **Region**: `eu-west-1`

Production and staging environments reference these resources via remote state data sources.

## Outputs

The following outputs are available for consumption via remote state:

### Primary Outputs (V1 - Used by Staging/Prod)
- `cache_policy_map` - Mapping of cache policy names to IDs (V1)
- `cache_policies` - Complete cache policies V1 configuration
- `waf_arn` - WAF ARN for CloudFront distributions
- `waf_count` - Number of WAF instances

### Alternative Outputs (V2)
- `cache_policy_map_v2` - Mapping of cache policy names to IDs (V2)
- `cache_policies_v2` - Complete cache policies V2 configuration

### Common Outputs
- `waf_config` - WAF configuration
- `environment` - Environment name
- `project_name` - Project name
