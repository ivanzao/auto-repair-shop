module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.29"

  vpc_id     = aws_vpc.main.id
  subnet_ids = [aws_subnet.private.id]

  cluster_endpoint_public_access = true

  eks_managed_node_groups = {
    default = {
      instance_types = [var.node_instance_type]

      min_size     = 2
      max_size     = 3
      desired_size = 2

      labels = {
        role = "general"
      }
    }
  }

  tags = {
    Environment = "production"
    Application = "auto-repair-shop"
  }
}
