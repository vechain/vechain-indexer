output "alb_arn" {
  description = "The ARN of the ALB"
  value       = length(aws_alb.alb) > 0 ? aws_alb.alb.id : ""
}

output "name" {
  description = "The name of the ALB"
  value       = length(aws_alb.alb) > 0 ? aws_alb.alb.name : ""
}

output "alb_arn_suffix" {
  description = "ALB ARN suffix — the `LoadBalancer` dimension on AWS/ApplicationELB metrics"
  value       = aws_alb.alb.arn_suffix
}

output "alb_tg_arn_suffix" {
  description = "Target group ARN suffix — the `TargetGroup` dimension on AWS/ApplicationELB metrics"
  value       = length(aws_alb_target_group.alb_target_group_https) > 0 ? aws_alb_target_group.alb_target_group_https[0].arn_suffix : ""
}

output "alb_tg" {
  description = "The ARN of the ALB Target Group"
  value       = length(aws_alb_target_group.alb_target_group_https) > 0 ? aws_alb_target_group.alb_target_group_https[0].arn : ""
}

output "nlb_tg" {
  description = "The ARN of the NLB Target Group"
  value       = length(aws_alb_target_group.nlb_target_group_tcp) > 0 ? aws_alb_target_group.nlb_target_group_tcp[0].arn : ""
}

output "service_name" {
  description = "The ECS service name"
  value       = aws_ecs_service.service_alb.name
}

# output all metric filter transformation names
output "log_metric_names" {
  description = "The names of the metric filters"
  value       = flatten(aws_cloudwatch_log_metric_filter.ecs_cw_log_metric_filter[*].metric_transformation[*].name)
}

output "alb_dns_name" {
  description = "The DNS name of the ALB"
  value       = length(aws_alb.alb) > 0 ? aws_alb.alb.dns_name : ""
}

output "cluster_name" {
  description = "The ECS cluster name"
  value       = aws_ecs_service.service_alb.cluster
}

output "ecs_task_execution_role_id" {
  description = "The id of the ECS execution task role, so it can be customised to allow additional fine-grained privileges"
  value       = aws_iam_role.ecs_task_execution_role.id
}

output "secrets_name" {
  description = "The name of the Secrets Manager secret."
  value       = var.secrets_enable ? aws_secretsmanager_secret.secrets[0].name : null
}

output "ecr_repository_url" {
  description = "The URL of the ECR repository"
  value       = var.is_create_repo ? aws_ecr_repository.repo[0].repository_url : null
}

output "ecr_repository_arn" {
  description = "The ARN of the ECR repository"
  value       = var.is_create_repo ? aws_ecr_repository.repo[0].arn : null
}

output "ecs_cloudwatch_log_group_name" {
  description = "The name of the CloudWatch log group for the ECS service"
  value       = aws_cloudwatch_log_group.ecs_cw_log_group.name
}

output "alb_http_listener_arn" {
  description = "The ARN of the ALB HTTP listener"
  value       = length(aws_alb_listener.alb_listener) > 0 ? aws_alb_listener.alb_listener[0].arn : ""
}

output "alb_https_listener_arn" {
  description = "The ARN of the ALB HTTPS listener"
  value       = length(aws_alb_listener.alb_listener_https) > 0 ? aws_alb_listener.alb_listener_https[0].arn : ""
}

output "alb_zone_id" {
  description = "Zone id for alb"
  value       = aws_alb.alb.zone_id
}