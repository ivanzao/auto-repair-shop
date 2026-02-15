variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "auto-repair-shop-cluster"
}

variable "db_password" {
  description = "Database password for PostgreSQL"
  type        = string
  sensitive   = true
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS node group"
  type        = string
  default     = "t3.medium"
}
