resource "mongodbatlas_project_ip_access_list" "mongo-db-atlas" {
  count      = local.env.environment == "prod" ? 1 : 0
  project_id = local.env.mongoatlas_project_id
  cidr_block = local.env.cidr
  comment    = "AWS VPC CIDR block"
}
