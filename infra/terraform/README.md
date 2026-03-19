# Infraestrutura - Terraform (AWS EKS)

## Estrutura de Modulos

```
infra/terraform/
├── main.tf              # Orquestra os modulos
├── variables.tf         # Variaveis do root
├── outputs.tf           # Outputs do root (re-exporta dos modulos)
├── providers.tf         # Provider AWS
├── modules/
│   ├── vpc/             # VPC, subnets, IGW, NAT Gateway, route tables
│   ├── eks/             # EKS cluster e node group
│   └── rds/             # RDS PostgreSQL, security group, subnet group
```

### Dependencias entre modulos

```
VPC → EKS (subnet IDs)
VPC + EKS → RDS (vpc_id, subnet IDs, EKS security group)
```

## Recursos Provisionados

| Modulo | Recursos |
|--------|----------|
| **vpc** | VPC, 2 subnets publicas, 2 subnets privadas, Internet Gateway, NAT Gateway, route tables |
| **eks** | EKS cluster (v1.35), managed node group (t3.small, 2-3 nodes) |
| **rds** | PostgreSQL 16 (db.t3.micro), DB subnet group, security group |

## Pre-requisitos

- [Terraform](https://www.terraform.io/downloads) >= 1.5.0
- [AWS CLI](https://aws.amazon.com/cli/) configurado com credenciais
- [kubectl](https://kubernetes.io/docs/tasks/tools/) para interagir com o cluster

## Variaveis

| Variavel | Descricao | Default |
|----------|-----------|---------|
| `aws_region` | Regiao AWS | `us-east-1` |
| `cluster_name` | Nome do cluster EKS | `auto-repair-shop-cluster` |
| `db_password` | Senha do PostgreSQL (sensivel) | - |
| `node_instance_type` | Tipo da instancia EC2 | `t3.small` |

## Como Aplicar

```bash
# Inicializar providers e modulos
terraform init

# Visualizar plano de execucao
terraform plan -var="db_password=YOUR_DB_PASSWORD"

# Aplicar infraestrutura
terraform apply -var="db_password=YOUR_DB_PASSWORD"

# Configurar kubectl para o cluster
aws eks update-kubeconfig --name auto-repair-shop-cluster --region us-east-1

# Verificar pods
kubectl get pods -n auto-repair-shop
```

## Como Destruir

```bash
terraform destroy -var="db_password=YOUR_DB_PASSWORD"
```

## Outputs

| Output | Descricao |
|--------|-----------|
| `cluster_endpoint` | Endpoint do cluster EKS |
| `cluster_name` | Nome do cluster EKS |
| `rds_endpoint` | Endpoint do PostgreSQL RDS |
