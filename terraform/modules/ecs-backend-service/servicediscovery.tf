resource "aws_service_discovery_service" "service" {
  name         = var.service_discovery_name != "" ? var.service_discovery_name : var.app_name
  namespace_id = var.namespace_id
  dns_config {
    namespace_id = var.namespace_id
    dns_records {
      type = "A"
      ttl  = var.cloudmap_ttl
    }

    dns_records {
      type = "SRV"
      ttl  = var.cloudmap_ttl
    }
  }
}