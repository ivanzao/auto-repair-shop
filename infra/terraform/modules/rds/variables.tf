variable "cluster_name" {
  description = "Cluster name used for resource naming"
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC"
  type        = string
}

variable "private_subnet_ids" {
  description = "IDs of the private subnets for the DB subnet group"
  type        = list(string)
}

variable "eks_security_group_id" {
  description = "Security group ID of the EKS cluster (allowed to access RDS)"
  type        = string
}

variable "db_password" {
  description = "Database password for PostgreSQL"
  type        = string
  sensitive   = true
}
