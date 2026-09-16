variable "region" {
  description = "The AWS Region Id"
  type        = string
  default     = "eu-west-1"
}

variable "env" {
  description = "The environment name"
  type        = string
  default     = "dev"
}

variable "project" {
  description = "The project name"
  type        = string
  default     = ""
}

variable "app_name" {
  description = "The application name"
  type        = string
  default     = ""
}

####################################
# variables for endpoints type interface
####################################
variable "vpcendpoints_interfaces" {
  description = "a map of object for creating vpcendpoints type interface (s3,kms,sns,...)"
  type = list(object({
    id                  = string
    vpc_id              = string
    subnet_ids          = list(string)
    security_group_ids  = list(string)
    private_dns_enabled = bool
    allowed_cidr_blocks = list(string)
    inbound_ports       = list(string)
    tags                = map(string)
  }))
  default = []
}

####################################
# variables for endpoints type gateway
####################################
variable "vpcendpoints_gateways" {
  description = "a map of object for creating vpcendpoints type gateway (s3,dynamodb,...)"
  type = list(object({
    id              = string
    vpc_id          = string
    route_table_ids = list(string)
    tags            = map(string)
  }))
  default = []
}
