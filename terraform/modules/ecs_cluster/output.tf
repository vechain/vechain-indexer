output "name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.ecs_cluster.name
}

output "arn" {
  description = "ECS cluster arn"
  value       = aws_ecs_cluster.ecs_cluster.arn
}

output "id" {
  description = "ECS cluster id"
  value       = aws_ecs_cluster.ecs_cluster.id
}
