# Rede privada isolada na AWS com subnets publicas e privadas em 2 AZs
module "vpc" {
  source = "./modules/vpc"

  cluster_name = var.cluster_name
}

# Cluster Kubernetes gerenciado onde a aplicacao roda
module "eks" {
  source = "./modules/eks"

  cluster_name        = var.cluster_name
  private_subnet_ids  = module.vpc.private_subnet_ids
  public_subnet_ids   = module.vpc.public_subnet_ids
  node_instance_type  = var.node_instance_type
  public_access_cidrs = var.public_access_cidrs
}

# Banco de dados PostgreSQL gerenciado, acessivel apenas pelo cluster EKS
module "rds" {
  source = "./modules/rds"

  cluster_name          = var.cluster_name
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  eks_security_group_id = module.eks.cluster_security_group_id
  db_password           = var.db_password
}
