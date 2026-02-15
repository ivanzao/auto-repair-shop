# Infraestrutura - Terraform (AWS EKS)

## Recursos Provisionados

| Recurso | Descricao |
|---------|-----------|
| **VPC** | Rede virtual com subnets publicas e privadas em 2 AZs |
| **EKS** | Cluster Kubernetes gerenciado (v1.29) com node group managed |
| **RDS** | PostgreSQL 16.4 (db.t3.micro) em subnet privada |
| **K8s Resources** | Namespace, ConfigMap, Secret, Deployment (2 replicas), Service (LoadBalancer), HPA (2-5 pods) |

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
| `jwt_secret` | Secret JWT para autenticacao (sensivel) | - |
| `mailersend_token` | Token da API MailerSend (sensivel) | - |
| `image_tag` | Tag da imagem Docker | `latest` |
| `node_instance_type` | Tipo da instancia EC2 | `t3.medium` |

## Como Aplicar

```bash
# Inicializar providers
terraform init

# Visualizar plano de execucao
terraform plan \
  -var="db_password=YOUR_DB_PASSWORD" \
  -var="jwt_secret=YOUR_JWT_SECRET" \
  -var="mailersend_token=YOUR_MAILERSEND_TOKEN"

# Aplicar infraestrutura
terraform apply \
  -var="db_password=YOUR_DB_PASSWORD" \
  -var="jwt_secret=YOUR_JWT_SECRET" \
  -var="mailersend_token=YOUR_MAILERSEND_TOKEN"

# Configurar kubectl para o cluster
aws eks update-kubeconfig --name auto-repair-shop-cluster --region us-east-1

# Verificar pods
kubectl get pods -n auto-repair-shop
```

## Como Destruir

```bash
terraform destroy \
  -var="db_password=YOUR_DB_PASSWORD" \
  -var="jwt_secret=YOUR_JWT_SECRET" \
  -var="mailersend_token=YOUR_MAILERSEND_TOKEN"
```

## Outputs

| Output | Descricao |
|--------|-----------|
| `cluster_endpoint` | Endpoint do cluster EKS |
| `cluster_name` | Nome do cluster EKS |
| `load_balancer_hostname` | Hostname do Load Balancer da aplicacao |
| `rds_endpoint` | Endpoint do PostgreSQL RDS |
