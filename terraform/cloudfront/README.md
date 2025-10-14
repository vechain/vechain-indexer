# CloudFront Terraform Workspace Configuration

This directory contains a unified Terraform configuration that uses workspaces to manage multiple environments (shared, staging, prod) from a single codebase.

## 🏗️ Architecture Overview

### **Workspace Structure**
```
terraform/cloudfront/
├── provider.tf                # Provider configuration with workspace logic
├── shared.tf                  # Shared resources (cache policies, WAF)
├── cloudfront.tf              # CloudFront distributions
├── outputs.tf                 # Workspace-aware outputs
├── terraform-workspace.sh     # Management script
├── environments/
│   ├── shared.yml             # Shared resources configuration
│   ├── staging.yml            # Staging environment configuration
│   └── prod.yml               # Production environment configuration
└── .github-workflows/
    └── cloudfront-deploy-workspace.yml  # GitHub Actions workflow
```

### **Workspaces**
- **`shared`** - Cache policies, WAF rules (deployed first)
- **`staging`** - Staging CloudFront distributions
- **`prod`** - Production CloudFront distributions + continuous deployment

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
# Deploy everything in correct order (shared → staging → prod)
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
    key    = "cloudfront/env:/shared/terraform.tfstate"
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
├── cloudfront/env:/shared/terraform.tfstate      # Shared resources
├── cloudfront/env:/staging/terraform.tfstate     # Staging environment  
└── cloudfront/env:/prod/terraform.tfstate        # Production environment
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

