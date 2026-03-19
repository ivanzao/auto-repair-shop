variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "private_subnet_ids" {
  description = "IDs of the private subnets for the EKS node group"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "IDs of the public subnets for the EKS cluster"
  type        = list(string)
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS node group"
  type        = string
}

variable "public_access_cidrs" {
  description = "CIDR blocks allowed to access the EKS API server public endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
