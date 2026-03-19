# Busca o account ID da conta AWS para montar o ARN da LabRole
data "aws_caller_identity" "current" {}

locals {
  lab_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/LabRole"
}

# Cluster Kubernetes gerenciado — control plane provisionado pela AWS
resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  role_arn = local.lab_role_arn
  version  = "1.35"

  vpc_config {
    subnet_ids              = concat(var.private_subnet_ids, var.public_subnet_ids)
    endpoint_public_access  = true
    endpoint_private_access = true
    public_access_cidrs     = var.public_access_cidrs
  }

  tags = {
    Environment = "production"
    Application = "auto-repair-shop"
  }
}

# Grupo de nodes EC2 que executam os pods da aplicacao
resource "aws_eks_node_group" "default" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-default"
  node_role_arn   = local.lab_role_arn
  subnet_ids      = var.private_subnet_ids

  instance_types = [var.node_instance_type]

  scaling_config {
    min_size     = 2
    max_size     = 3
    desired_size = 2
  }

  labels = {
    role = "general"
  }

  tags = {
    Environment = "production"
    Application = "auto-repair-shop"
  }

  depends_on = [aws_eks_cluster.main]
}
