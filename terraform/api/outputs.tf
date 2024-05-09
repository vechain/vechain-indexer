output "cluster_name" {
  description = "The name of the ECS cluster"
  value       = length(module.ecs-cluster) > 0 ? module.ecs-cluster.name : ""
}

output "api_ecs_service_names" {
  description = "The names of the load balanced ECS services"
  value       = length(module.ecs-lb-service-api) > 0 ? [for service in module.ecs-lb-service-api : service.service_name] : []
}

output "indexer_ecs_service_names" {
  description = "The names of the backend ECS services"
  value       = length(module.ecs-backend-service) > 0 ? [for service in module.ecs-backend-service : service.service_name] : []
}

output "load_balancer_domain_mainnet" {
  description = "The domain name of the mainnet load balancer"
  value       = length(module.ecs-lb-service-api) > 0 ? module.ecs-lb-service-api["main"].alb_dns_name : ""
}

output "load_balancer_domain_testnet" {
  description = "The domain name of the testnet load balancer"
  value       = length(module.ecs-lb-service-api) > 0 ? module.ecs-lb-service-api["test"].alb_dns_name : ""
}
