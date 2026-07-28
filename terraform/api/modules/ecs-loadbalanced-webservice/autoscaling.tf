######################################################
/* ECS Fargate, there isn't an Auto Scaling Group in the traditional sense as with EC2 instances. 
Fargate is a serverless compute engine for containers, and it automatically scales based on the number of tasks you define in your ECS service.

However, you can still set up auto-scaling policies based on CPU and memory utilization for your ECS Fargate service. 
Below is a Terraform module example for setting up an ECS Fargate service with auto-scaling policies: */
######################################################

data "aws_ecs_cluster" "ecs" {
  cluster_name = var.autoscale_cluster_name
}
resource "aws_appautoscaling_target" "ecs_target" {
  count = var.enable_ecs_cpu_based_autoscaling || var.enable_ecs_memory_based_autoscaling ? 1 : 0

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
  resource_id  = "service/${data.aws_ecs_cluster.ecs.cluster_name}/${aws_ecs_service.service_alb.name}"

  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_service_cpu_policy" {
  count = var.enable_ecs_cpu_based_autoscaling ? 1 : 0

  name               = "${var.name}-service-cpu"
  resource_id        = aws_appautoscaling_target.ecs_target[0].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target[0].service_namespace
  policy_type        = "TargetTrackingScaling"

  target_tracking_scaling_policy_configuration {
    target_value       = var.target_cpu_value
    disable_scale_in   = var.disable_scale_in
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "ecs_service_memory_policy" {
  count = var.enable_ecs_memory_based_autoscaling ? 1 : 0

  name               = "${var.name}-service-memory"
  resource_id        = aws_appautoscaling_target.ecs_target[0].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target[0].service_namespace
  policy_type        = "TargetTrackingScaling"

  target_tracking_scaling_policy_configuration {
    target_value       = var.target_memory_value
    disable_scale_in   = var.disable_scale_in
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
  }
}
