output "ecr_arn" {
  description = "The ARN of the ECR repository"
  value       = try(aws_ecr_repository.ecr[0].arn, "")
}

output "registry_id" {
  value       = try(aws_ecr_repository.ecr[0].registry_id, "")
  description = "The registry ID where the repository was created"
}

output "repository_url" {
  value       = try(aws_ecr_repository.ecr[0].repository_url, "")
  description = "The URL of the repository (in the form aws_account_id.dkr.ecr.region.amazonaws.com/repositoryName)"
}

output "registry_scan_configuration_id" {
  description = "The ID of the registry scanning configuration"
  value       = try(aws_ecr_registry_scanning_configuration.configuration[0].registry_id, null)
}

output "scan_rules" {
  description = "The scanning rules configured"
  value       = try(aws_ecr_registry_scanning_configuration.configuration[0].rule, null)
}