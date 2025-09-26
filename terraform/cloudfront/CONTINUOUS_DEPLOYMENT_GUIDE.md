# CloudFront Continuous Deployment Setup Guide

This guide explains how the staging CloudFront distribution domain names are integrated into the production environment for continuous deployment policies.

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐
│  Staging Env    │    │  Production Env │
│                 │    │                 │
│ staging/        │    │ prod/           │
│ ├── cloudfront  │◄───┤ ├── cloudfront  │
│ ├── outputs.tf  │    │ ├── outputs.tf  │
│ └── ...         │    │ └── ...         │
└─────────────────┘    └─────────────────┘
        │                       ▲
        │                       │
        ▼                       │
┌─────────────────┐             │
│   S3 Backend    │─────────────┘
│                 │
│ staging/state   │
│ prod/state      │
└─────────────────┘
```

## 🎛️ Environment Configuration

### **Staging Environment Characteristics:**
- **CNAMEs**: Empty array `[]` - Uses default CloudFront domain names only
- **Access URLs**: `d123abc456.cloudfront.net` (AWS-generated CloudFront domains)
- **Purpose**: Simplified staging setup without custom domain SSL certificate requirements
- **Benefits**: Faster setup, no DNS configuration needed, cost-effective

### **Production Environment Characteristics:**
- **CNAMEs**: Custom domains like `indexer.mainnet.vechain.org`
- **Access URLs**: Branded custom domains for end users
- **Purpose**: Production-ready with proper branding and SSL certificates

## 📋 Components

### 🎯 Staging Environment (`staging/`)

#### 📄 `outputs.tf`
Exposes staging CloudFront domain names for consumption by production:

```hcl
output "staging_mainnet_cloudfront_domain_name" {
  description = "Staging mainnet CloudFront domain (xyz.cloudfront.net)"
  value       = module.staging_mainnet_cloudfront.cloudfront_distribution_domain_name
}

output "staging_cloudfront_domains" {
  description = "All staging CloudFront domains for CD policies"
  value = {
    mainnet = { domain_name = "..." }
    testnet = { domain_name = "..." }
  }
}
```

### 🎯 Production Environment (`prod/`)

#### 📊 Remote State Data Source
Reads staging outputs via Terraform remote state:

```hcl
data "terraform_remote_state" "staging" {
  backend = "s3"
  config = {
    bucket = "your-terraform-state-bucket"
    key    = "staging/veworld-indexer-cloudfront.tfstate"
    region = "eu-west-1"
  }
}
```

#### 🔄 Continuous Deployment Policies
Creates CD policies using staging domain names:

```hcl
resource "aws_cloudfront_continuous_deployment_policy" "mainnet_continuous_deployment" {
  enabled = true
  
  staging_distribution_dns_names {
    items = [
      data.terraform_remote_state.staging.outputs.staging_mainnet_cloudfront_domain_name
    ]
    quantity = 1
  }
  
  traffic_config {
    type = "SingleHeader"
    single_header_config {
      header = "aws-cf-cd-staging"
      value  = "mainnet"
    }
  }
}
```

#### 🌐 CloudFront Integration
Associates CD policies with production distributions:

```hcl
module "mainnet_cloudfront" {
  # ... other configuration
  
  continuous_deployment_policy_id = aws_cloudfront_continuous_deployment_policy.mainnet_continuous_deployment.id
}
```

## 🚀 How It Works

### 1. **Staging Deployment**
```bash
cd staging/
terraform apply
# Creates staging CloudFront distributions
# Outputs domain names to state file
```

### 2. **Production Deployment**
```bash
cd prod/
terraform apply
# Reads staging state via remote state data source
# Creates CD policies using staging domains
# Associates CD policies with production distributions
```

### 3. **Traffic Routing**
- **Normal Traffic**: Goes to production CloudFront distributions
- **Staging Traffic**: Requests with header `aws-cf-cd-staging: mainnet` → staging mainnet distribution
- **Staging Traffic**: Requests with header `aws-cf-cd-staging: testnet` → staging testnet distribution

## 📊 Outputs Available

### From Production (`terraform output`)
```json
{
  "staging_domains_for_cd": {
    "mainnet": "d123abc456.cloudfront.net",
    "testnet": "d789def012.cloudfront.net"
  },
  "continuous_deployment_policies": {
    "mainnet": {
      "policy_id": "CDP123ABC456",
      "staging_domain": "d123abc456.cloudfront.net",
      "traffic_header": "aws-cf-cd-staging",
      "traffic_header_value": "mainnet"
    }
  },
  "cloudfront_domains_all_environments": {
    "production": {
      "mainnet": "d999prod001.cloudfront.net",
      "testnet": "d888prod002.cloudfront.net"
    },
    "staging": {
      "mainnet": "d123abc456.cloudfront.net",
      "testnet": "d789def012.cloudfront.net"
    }
  }
}
```

## 🧪 Testing Continuous Deployment

### Test Staging Environment
```bash
# Route production domain traffic to staging mainnet distribution
curl -H "aws-cf-cd-staging: mainnet" https://indexer.mainnet.vechain.org/api/v1/health

# Route production domain traffic to staging testnet distribution
curl -H "aws-cf-cd-staging: testnet" https://indexer.testnet.vechain.org/api/v1/health

# Or test staging directly using CloudFront domain names
curl https://d123abc456.cloudfront.net/api/v1/health  # Direct staging access
```

### Normal Production Traffic
```bash
# Normal production traffic (no special header)
curl https://indexer.mainnet.vechain.org/api/v1/health
```

## 🎯 CI/CD Integration

### Pipeline Example
```yaml
stages:
  deploy-staging:
    script:
      - cd staging/
      - terraform apply -auto-approve
    
  deploy-production:
    script:
      - cd prod/
      - terraform apply -auto-approve  # Automatically picks up new staging domains
    
  test-continuous-deployment:
    script:
      - |
        # Get staging domains from terraform output
        STAGING_DOMAIN=$(cd prod && terraform output -json staging_domains_for_cd | jq -r '.mainnet')
        
        # Test staging traffic routing
        curl -H "aws-cf-cd-staging: mainnet" https://indexer.mainnet.vechain.org/health
        
        # Validate response comes from staging
        echo "✅ Continuous deployment working with staging domain: $STAGING_DOMAIN"
```

## ⚙️ Configuration Requirements

### 1. **S3 Backend Setup**
Update the bucket name in `prod/cloudfront.tf`:
```hcl
data "terraform_remote_state" "staging" {
  backend = "s3"
  config = {
    bucket = "your-actual-terraform-state-bucket"  # 👈 Update this
    key    = "staging/veworld-indexer-cloudfront.tfstate"
    region = "eu-west-1"
  }
}
```

### 2. **State File Paths**
Ensure staging state is stored at:
- Path: `staging/veworld-indexer-cloudfront.tfstate`
- Bucket: Same as production but different key prefix

### 3. **Module Support**
Verify your CloudFront module supports the `continuous_deployment_policy_id` parameter:
```hcl
# In your terraform_infrastructure_modules CloudFront module:
variable "continuous_deployment_policy_id" {
  description = "ID of the continuous deployment policy"
  type        = string
  default     = null
}
```

## 🔧 Troubleshooting

### Issue: "staging outputs not found"
```bash
# Check if staging is deployed
cd staging/
terraform output

# Verify remote state access
cd prod/
terraform refresh
```

### Issue: "Module doesn't support continuous_deployment_policy_id"
- Update your CloudFront module to accept the parameter
- Or remove the `continuous_deployment_policy_id` lines temporarily

### Issue: "S3 access denied"
- Verify IAM permissions for cross-environment state access
- Check bucket policy allows read access to staging state

## 📈 Benefits

- **🔄 Automated CD Setup**: Staging domains automatically update in production
- **🧪 Safe Testing**: Test changes with header-based traffic routing
- **📊 Full Visibility**: All domains and policies exposed via outputs
- **🚀 CI/CD Ready**: Perfect for automated deployment pipelines
- **🔒 Environment Isolation**: Staging and prod remain completely separate

Your continuous deployment setup is now ready! 🎉
