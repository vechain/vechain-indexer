# Introduction

This repository contains vpc related configurations.

In order to configure VPC below AWS profiles are used:

- veworld-dev-devops
- veworld-prod-devops

Profile `veworld-dev-devops` contains credentials for the dev account. It is in account `937628727224`.
Profile `veworld-prod-devops` contains credentials for the production account. It is in account `905964754131`
and administered by control tower from the root account `445211916558`, so comes with a pre-provisioned vpc.

To authenticate with terraform please follow the below document:
`https://vechain.atlassian.net/wiki/spaces/Devops/pages/183435265/Playing+nice+with+Okta+AWS+SSO+and+Terraform`

# Usage

In order to plan; at the minimum the following commands needs to be run
```bash
cd api
terraform workspace select dev|prod
terraform apply -target='module.vpc[0].aws_vpc.this[0]'
terraform plan
```

To then apply; build out the vpc then the rest with
```bash
terraform apply -target=module.vpc
terraform apply
```
