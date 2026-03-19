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

## Sensitive — pass via environment variable: export TF_VAR_db_password="..."
variable "db_password" {
  description = "Database password for PostgreSQL"
  type        = string
  sensitive   = true
}

variable "public_access_cidrs" {
  description = "CIDR blocks allowed to access the EKS API server public endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS node group"
  type        = string
  default     = "t3.small"
}
