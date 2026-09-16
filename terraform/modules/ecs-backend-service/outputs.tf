output "service_name" {
  description = "The ECS service name"
  value       = aws_ecs_service.service.name
}

# output all metric filter transformation names
output "log_metric_names" {
  description = "The names of the metric filters"
  value       = flatten(aws_cloudwatch_log_metric_filter.ecs_cw_log_metric_filter[*].metric_transformation[*].name)
}

output "ecs_cloudwatch_log_group_name" {
  description = "The name of the CloudWatch log group for the ECS service"
  value       = aws_cloudwatch_log_group.ecs_cw_log_group.name
}

output "cluster_name" {
  description = "The ECS cluster name"
  value       = aws_ecs_service.service.cluster
}
