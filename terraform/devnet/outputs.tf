output "cluster_name" {
  description = "The name of the ECS cluster"
  value       = length(module.ecs-cluster) > 0 ? module.ecs-cluster.name : ""
}

output "load_balancer_domain_devnet" {
  description = "The domain name of the mainnet load balancer"
  value       = length(module.ecs-lb-service-api) > 0 ? module.ecs-lb-service-api["dev"].alb_dns_name : ""
}

output "devnet_domain" {
  description = "The domain name of the devnet environment"
  value       = aws_route53_record.devnet.fqdn
}