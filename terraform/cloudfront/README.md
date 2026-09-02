# CloudFront Terraform Workspace Configuration

This directory contains a unified Terraform configuration that uses workspaces to manage multiple environments (shared, staging, prod, dead) from a single codebase.

## Workspace apply status

| Workspace | Plan |
|---|---|
| `prod` | No changes |
| `staging` | No changes |
| `shared` | No changes |
| `dead` | Never applied — creates the dead-colour distributions on first run |

[deploy.yml](../../.github/workflows/deploy.yml) applies
the stack after the VPC one, `shared` then `staging` then `prod` then `dead` — that
order is a dependency chain, since the distributions consume `shared`'s cache
policies and `prod`'s continuous-deployment policy reads `staging`'s domains. Each
workspace's plan lands in the job summary before it applies. `shared-infra.yml`
plans all four at review time — read that before merging.

## The dead workspace

`dead` fronts the inactive blue/green colour so it can be tested before a cutover.
Its origins are the colour-agnostic `*.dead.prod.veworld.vechain.org` records, so a
cutover swaps what it points at without any change here.

It answers on:

- `https://mainnet.dead.veworld.vechain.org`
- `https://testnet.dead.veworld.vechain.org`

Unlike the other workspaces it owns its certificate and DNS: `dead_dns.tf` issues a
DNS-validated us-east-1 certificate covering both names and writes the alias records
into the `veworld.vechain.org` zone, looked up by name. That zone is the shortest one
this account holds — the live `indexer.*.vechain.org` names sit in another account,
so this stack cannot write DNS beside them.

The first apply blocks on ACM DNS validation, usually a few minutes.

It reuses the prod WAF ACLs and prod's cache behaviours (via `cache_behaviors_from`
in `environments/dead.yml`) — a dead-colour test that cached differently from live
would not be testing the same thing. The WAF rate limit applies, so heavy suites
need the `x-rate-limit-bypass` header the same way prod does.

`shared` now tracks only the cache policies. It once held the two CLOUDFRONT-scope
WAF ACLs that `terraform/vpc/cloudfront_waf.tf` has owned since #1519; the
`removed` blocks in `shared.tf` forgot them rather than destroying them, and
having been applied, those blocks are spent and can go. The distributions take
their WAF ARNs from `terraform/vpc` via remote state.

## Origin verification

Every distribution sends `x-origin-verify` to the origin, holding the value of the
Secrets Manager secret `/prod/veworld/cloudfront-origin-verify-token` in
`eu-west-1`. `null_resource.ensure_origin_verify_secret` creates the secret on
first apply if it is missing, so whichever of `prod`/`staging` applies first wins
and the other reuses the value.

CloudFront overwrites any same-named header a viewer sends, so a client cannot
forge it through the distribution. Once the ALB requires it, that is what stops
someone pointing their own distribution at the origin — the CloudFront
origin-facing prefix list on the ALB security group admits any distribution in
any AWS account.

**Apply order matters.** Both `prod` and `staging` must be applied here *before*
`terraform/api` starts enforcing the header, or the ALB will reject live traffic.

Enforcement is off until `alb.origin_verify_header_name` is set in
`terraform/api/environments/prod-{blue,green}.yml`; empty means the ALB adds no
header condition. To switch it on: apply both workspaces here, confirm the header
is reaching the origin, then set that key to `x-origin-verify` in both colour
files.

Rotation needs the ALB to accept both values while the distributions catch up —
apply either side alone and the other is still sending, or still demanding, the
value that no longer matches. `alb.origin_verify_accept_previous` adds the
secret's `AWSPREVIOUS` alongside `AWSCURRENT` for that window:

1. Update the secret. The old value becomes `AWSPREVIOUS`; nothing has switched.
2. Set `origin_verify_accept_previous: true` in both colour files, apply
   `terraform/api`. The ALB now takes either value.
3. Apply `prod` and `staging` here. The distributions move to the new value.
4. Set it back to `false`, apply `terraform/api`. The old value stops working.

Step 2 reads `AWSPREVIOUS`, so it only works after step 1 has created one.

## 🏗️ Architecture Overview

### **Workspace Structure**
```
terraform/cloudfront/
├── provider.tf                # Provider configuration with workspace logic
├── shared.tf                  # Shared resources (cache policies, WAF)
├── cloudfront.tf              # CloudFront distributions
├── dead_dns.tf                # Dead-colour alias records
├── outputs.tf                 # Workspace-aware outputs
├── terraform-workspace.sh     # Management script
├── environments/
│   ├── shared.yml             # Shared resources configuration
│   ├── staging.yml            # Staging environment configuration
│   ├── prod.yml               # Production environment configuration
│   └── dead.yml               # Dead blue/green colour configuration
└── (no CI workflow yet — applies are manual)
```

### **Workspaces**
- **`shared`** - Cache policies, WAF rules (deployed first)
- **`staging`** - Continuous-deployment canary distributions. Same prod origins,
  no aliases, and idle until a continuous-deployment policy routes to them, so
  they must be applied alongside `prod` to stay a faithful copy of it
- **`prod`** - Production CloudFront distributions + continuous deployment
- **`dead`** - Distributions fronting the inactive blue/green colour, so it can be
  tested before a cutover. See "The dead workspace" above

## 🚀 Quick Start

### **1. Initialize and Setup**
```bash
# Navigate to the workspace directory
cd terraform/cloudfront

# Initialize Terraform and create workspaces
./terraform-workspace.sh init

# List available workspaces
./terraform-workspace.sh list
```

### **2. Deploy All Environments**
```bash
# Deploy staging → prod → dead. shared is left to an explicit apply.
./terraform-workspace.sh deploy-all
```

### **3. Deploy Individual Workspaces**
```bash
# Deploy shared resources only
./terraform-workspace.sh plan shared
./terraform-workspace.sh apply shared

# Deploy staging environment
./terraform-workspace.sh plan staging  
./terraform-workspace.sh apply staging

# Deploy production environment
./terraform-workspace.sh plan prod
./terraform-workspace.sh apply prod

# Deploy the dead-colour front door
./terraform-workspace.sh plan dead
./terraform-workspace.sh apply dead
```

## 📋 Management Commands

### **Workspace Management**
```bash
# Show current workspace
terraform workspace show

# List all workspaces
terraform workspace list

# Switch to specific workspace
terraform workspace select <workspace>

# Create new workspace
terraform workspace new <workspace>
```

### **Using the Management Script**
```bash
./terraform-workspace.sh <command> [workspace] [args]

Commands:
  init                     - Initialize and create workspaces
  plan <workspace>         - Plan deployment for workspace
  apply <workspace>        - Apply deployment for workspace
  destroy <workspace>      - Destroy resources in workspace
  deploy-all               - Deploy all environments in order
  list                     - List all workspaces
  show <workspace>         - Show workspace state
  select <workspace>       - Select workspace
  validate                 - Validate configuration
```

## 🔄 Migration from Folder-Based Structure

### **Before (Folder-Based)**
```
terraform/cloudfront/
├── shared/
├── staging/
└── prod/
```

### **After (Workspace-Based)**
```
terraform/cloudfront/
# Single directory with workspace separation
```

### **State Migration Steps**

⚠️ **Important**: This is a destructive operation. Take backups before proceeding.

```bash
# 1. Initialize new workspace structure
cd terraform/cloudfront
./terraform-workspace.sh init

# 2. Import existing state (if needed)
# This depends on your current state management approach

# 3. Test deployments in staging first
./terraform-workspace.sh plan staging
./terraform-workspace.sh apply staging

# 4. Validate everything works before proceeding to prod
```

## 🎯 Benefits of Workspace Approach

### **✅ Advantages**
- **Single codebase** - One configuration for all environments
- **DRY principle** - No code duplication between environments
- **Consistent configuration** - Same modules and logic across environments
- **Easier maintenance** - Update once, deploy everywhere
- **Workspace isolation** - State files are completely separate
- **Simplified CI/CD** - One pipeline handles all environments

### **⚠️ Considerations**
- **Shared state storage** - All workspaces use same S3 bucket (different keys)
- **Workspace awareness** - Configuration must be workspace-aware
- **Migration complexity** - Moving from folders requires careful planning

## 🔧 Configuration Details

### **Environment Configuration (YAML)**
Each workspace loads its configuration from `environments/{workspace}.yml`:

```yaml
# environments/staging.yml
environment: "staging"
project_name: "veworld"
region: "eu-west-1"
enable_continuous_deployment: false
# ... environment-specific settings
```

### **Workspace Logic**
The configuration uses Terraform workspace context:

```hcl
locals {
  workspace = terraform.workspace
  env_config = yamldecode(file("environments/${local.workspace}.yml"))
  
  is_shared   = local.workspace == "shared"
  is_staging  = local.workspace == "staging"
  is_prod     = local.workspace == "prod"
}
```

### **Remote State References**
Workspaces reference each other's state using the workspace key prefix:

```hcl
data "terraform_remote_state" "shared" {
  backend = "s3"
  config = {
    bucket = "veworld-indexer-terraform-state-prod"
    key    = "cloudfront/shared/cloudfront/terraform.tfstate"
    region = "eu-west-1"
  }
}
```

## 🔒 Security and Access Control

### **GitHub Environments**
Create these environments in GitHub with appropriate approvals:

- `shared` - No approval required
- `staging` - No approval required  
- `prod-mainnet-cloudfront-promotion-approval` - Requires approval
- `prod-testnet-cloudfront-promotion-approval` - Requires approval

### **AWS Permissions**
The GitHub workflow uses AWS role assumption:

```yaml
- name: Configure AWS Credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    aws-region: eu-west-1
    role-to-assume: ${{ secrets.AWS_ACC_ROLE }}
```

## 📊 State Management

### **State File Organization**
```
S3 Bucket: veworld-indexer-terraform-state-prod
├── cloudfront/shared/cloudfront/terraform.tfstate    # Shared resources
├── cloudfront/staging/cloudfront/terraform.tfstate   # Staging environment
├── cloudfront/prod/cloudfront/terraform.tfstate      # Production environment
└── cloudfront/dead/cloudfront/terraform.tfstate      # Dead blue/green colour
```

### **Workspace Commands**
```bash
# Show current state
terraform workspace select <workspace>
terraform show

# List resources in workspace
terraform state list

# Get specific output
terraform output <output_name>
```

## 🔍 Troubleshooting

### **Common Issues**

1. **Workspace doesn't exist**
   ```bash
   terraform workspace new <workspace>
   ```

2. **Configuration validation fails**
   ```bash
   terraform validate
   ```

3. **Remote state not found**
   - Ensure the referenced workspace has been deployed
   - Check S3 bucket and key paths

4. **YAML configuration errors**
   - Validate YAML syntax in environment files
   - Ensure all required keys are present

### **Debugging Tips**

```bash
# Check current workspace
terraform workspace show

# Validate configuration in current workspace
terraform validate

# Show plan without applying
terraform plan

# Check what resources exist
terraform state list

# Get detailed state information
terraform show
```

## 🚀 Next Steps

1. **Test the new structure** in a non-production environment
2. **Migrate gradually** - start with shared resources
3. **Update CI/CD pipelines** to use workspace commands
4. **Train team members** on workspace commands and concepts
5. **Monitor deployments** and adjust as needed

## 📚 Additional Resources

- [Terraform Workspaces Documentation](https://developer.hashicorp.com/terraform/language/state/workspaces)
- [AWS Provider Documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [CloudFront Module Documentation](https://github.com/vechainfoundation/terraform_infrastructure_modules)

