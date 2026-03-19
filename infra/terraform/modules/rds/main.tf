# Grupo de subnets onde o RDS pode ser provisionado (exige 2 AZs)
resource "aws_db_subnet_group" "main" {
  name       = "${var.cluster_name}-db-subnet"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name = "${var.cluster_name}-db-subnet-group"
  }
}

# Firewall do RDS — permite conexao apenas vinda do cluster EKS na porta 5432
resource "aws_security_group" "rds" {
  name_prefix = "${var.cluster_name}-rds-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.eks_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-rds-sg"
  }
}

# Instancia PostgreSQL gerenciada — banco de dados da aplicacao
resource "aws_db_instance" "postgres" {
  identifier = "${var.cluster_name}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp2"

  db_name  = "auto_repair_shop"
  username = "app"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  skip_final_snapshot = true
  multi_az            = false
  publicly_accessible = false

  tags = {
    Name        = "${var.cluster_name}-postgres"
    Environment = "production"
  }
}
