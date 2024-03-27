output "main_api_dns_name" {
  description = "The DNS name of the mainnet API"
  value       = length(module.ecs-lb-service-api["main"].alb_dns_name) > 0 ? module.ecs-lb-service-api["main"].alb_dns_name : ""
}

output "test_api_dns_name" {
  description = "The DNS name of the testnet API"
  value       = length(module.ecs-lb-service-api["test"].alb_dns_name) > 0 ? module.ecs-lb-service-api["test"].alb_dns_name : ""
}

output "cluster_name" {
  description = "The name of the ECS cluster"
  value       = length(module.ecs-cluster) > 0 ? module.ecs-cluster[0].name : ""
}

output "api_ecs_service_names" {
  description = "The names of the load balanced ECS services"
  value       = length(module.ecs-lb-service-api) > 0 ? [for service in module.ecs-lb-service-api : service.service_name] : []
}

output "indexer_ecs_service_names" {
  description = "The names of the backend ECS services"
  value       = length(module.ecs-backend-service) > 0 ? [for service in module.ecs-backend-service : service.service_name] : []
}
