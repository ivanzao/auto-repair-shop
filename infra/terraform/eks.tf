data "aws_caller_identity" "current" {}

locals {
  lab_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/LabRole"
}

resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  role_arn = local.lab_role_arn
  version  = "1.35"

  vpc_config {
    subnet_ids              = [aws_subnet.private.id, aws_subnet.private_b.id, aws_subnet.public.id, aws_subnet.public_b.id]
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  tags = {
    Environment = "production"
    Application = "auto-repair-shop"
  }
}

resource "aws_eks_node_group" "default" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-default"
  node_role_arn   = local.lab_role_arn
  subnet_ids      = [aws_subnet.private.id, aws_subnet.private_b.id]

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
