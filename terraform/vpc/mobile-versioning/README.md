# Introduction

This folder contains IAC component to stand up veworld mobile versioning cloudfront site

# Usage

Used by veworld mobile app to track later releases, changelogs and latest version information
S3 distribution content updated via GitHub workflow

Run Once configuration to create public cloudfront distribution over S3 web bucket after vpc creation
```bash
terraform init 
terraform workspace select 
terraform apply
```
