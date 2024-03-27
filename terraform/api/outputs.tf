output "security_group_alb_id" {
  description = "The ID of ALB the security group"
  value       = aws_security_group.alb-sg[0].id
}

output "security_group_ecs_service_id" {
  description = "The ID of the security group"
  value       = aws_security_group.ecs_service_sg[0].id
}

output "main_api_dns_name" {
    description = "The DNS name of the mainnet API"
    value       = module.ecs-lb-service-api["main"].alb_dns_name
}

output "test_api_dns_name" {
    description = "The DNS name of the testnet API"
    value       = module.ecs-lb-service-api["test"].alb_dns_name
}
