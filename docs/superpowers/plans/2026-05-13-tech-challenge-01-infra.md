# Plan 1: infra — VPC, EKS, Observability, RDS (single repo, 2 Terraform sub-projects) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o repositório `auto-repair-shop-infra` contendo dois sub-projetos Terraform com state separado: `network-k8s/` (VPC + EKS + AWS LB Controller + observability via Helm + IRSA) e `database/` (RDS PostgreSQL 16 + 2 databases + 2 app users + 3 secrets). CI/CD único com path-filter que aplica só o sub-projeto que mudou.

**Architecture:** Monorepo de infra com sub-projetos Terraform isolados por state file (`infra/network-k8s/...` e `infra/database/...` no mesmo S3 bucket). Database lê outputs do network-k8s via `terraform_remote_state`. Pipelines com path-filter evitam re-apply desnecessário. Decisão consolidada em **ADR-012**.

**Tech Stack:** Terraform 1.9+, AWS provider, Helm provider, Kubernetes provider, cyrilgdn/postgresql provider, Helm charts oficiais.

**Justificativa do merge (vs separar conforme enunciado):** Reduz duplicação de backend/providers/workflows/secrets; cross-cutting changes ficam atômicas; sub-projetos com state separado preservam blast radius. Documentado em ADR-012 e a ser justificado no vídeo de demo.

---

## Pré-requisitos

- AWS Academy session ativa
- `gh`, `aws`, `terraform`, `kubectl` instalados
- Spec aprovada

---

## File Structure

```
auto-repair-shop-infra/
├── .github/workflows/
│   ├── pr-check.yaml             # path-filter: fmt+validate+plan por sub-projeto
│   └── deploy.yaml               # path-filter: apply só do que mudou
├── .gitignore
├── README.md
├── scripts/
│   └── bootstrap-state-bucket.sh
├── network-k8s/
│   ├── backend.tf                # key: infra/network-k8s/terraform.tfstate
│   ├── providers.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── main.tf
│   ├── ssm-outputs.tf
│   ├── lambda-sg.tf
│   ├── irsa-app-sns.tf
│   ├── modules/{vpc,eks,alb-controller,namespaces,observability}/
│   └── helm-values/
└── database/
    ├── backend.tf                # key: infra/database/terraform.tfstate
    ├── providers.tf              # aws + postgresql
    ├── variables.tf
    ├── outputs.tf
    ├── main.tf
    ├── data.tf                   # remote_state da network-k8s
    ├── ssm-outputs.tf
    ├── modules/rds/
    ├── databases.tf
    ├── users.tf
    └── secrets.tf
```

---

## Task 1: Bootstrap do repositório e S3 state bucket

**Files:**
- Create: GitHub repo `auto-repair-shop-infra`, `.gitignore`, `scripts/bootstrap-state-bucket.sh`

- [ ] **Step 1: Criar repo, clonar, scaffold**

```bash
gh repo create ivanzao/auto-repair-shop-infra \
  --private \
  --description "Terraform monorepo: network-k8s + database sub-projects" \
  --clone
cd auto-repair-shop-infra

mkdir -p .github/workflows scripts network-k8s/modules/{vpc,eks,alb-controller,namespaces,observability} network-k8s/helm-values database/modules/rds
```

- [ ] **Step 2: `.gitignore`**

```bash
cat > .gitignore <<'EOF'
.terraform/
*.tfstate
*.tfstate.*
*.tfvars
*.tfvars.json
crash.log
crash.*.log
.idea/
*.swp
EOF
```

- [ ] **Step 3: Script bootstrap S3**

```bash
cat > scripts/bootstrap-state-bucket.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
REGION=us-east-1
BUCKET="auto-repair-shop-tfstate-$(aws sts get-caller-identity --query Account --output text)"
if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "Bucket $BUCKET already exists"
else
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGION"
  aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
  aws s3api put-bucket-encryption --bucket "$BUCKET" \
    --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  echo "Created: $BUCKET"
fi
EOF
chmod +x scripts/bootstrap-state-bucket.sh
```

- [ ] **Step 4: Rodar bootstrap + adicionar collaborator + commit inicial**

```bash
./scripts/bootstrap-state-bucket.sh
gh repo edit ivanzao/auto-repair-shop-infra --add-collaborator soat-architecture

git add .gitignore scripts/
git commit -m "chore: bootstrap monorepo with S3 state bucket script"
git push -u origin main
```

---

## Task 2: `network-k8s/` — backend, providers, variables

**Files:**
- Create: `network-k8s/backend.tf`, `network-k8s/providers.tf`, `network-k8s/variables.tf`

- [ ] **Step 1: `network-k8s/backend.tf`** (substituir `<ACCOUNT_ID>`)

```hcl
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws        = { source = "hashicorp/aws",        version = "~> 5.60" }
    helm       = { source = "hashicorp/helm",       version = "~> 2.13" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.30" }
    tls        = { source = "hashicorp/tls",        version = "~> 4.0" }
    http       = { source = "hashicorp/http",       version = "~> 3.4" }
  }
  backend "s3" {
    bucket  = "auto-repair-shop-tfstate-<ACCOUNT_ID>"
    key     = "infra/network-k8s/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
```

- [ ] **Step 2: `network-k8s/providers.tf`**

```hcl
provider "aws" { region = var.aws_region }

data "aws_eks_cluster_auth" "this" { name = module.eks.cluster_name }

provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_ca)
  token                  = data.aws_eks_cluster_auth.this.token
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_ca)
    token                  = data.aws_eks_cluster_auth.this.token
  }
}
```

- [ ] **Step 3: `network-k8s/variables.tf`**

```hcl
variable "aws_region"         { type = string default = "us-east-1" }
variable "cluster_name"       { type = string default = "auto-repair-shop-cluster" }
variable "vpc_cidr"           { type = string default = "10.0.0.0/16" }
variable "node_instance_type" { type = string default = "t3.medium" }
variable "node_desired_size"  { type = number default = 2 }
variable "node_min_size"      { type = number default = 2 }
variable "node_max_size"      { type = number default = 4 }
```

- [ ] **Step 4: Substituir ACCOUNT_ID + commit**

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i.bak "s/<ACCOUNT_ID>/$ACCOUNT_ID/" network-k8s/backend.tf && rm network-k8s/backend.tf.bak

git add network-k8s/{backend,providers,variables}.tf
git commit -m "feat(network-k8s): backend, providers, variables"
git push
```

---

## Task 3: `network-k8s/modules/vpc/`

**Files:**
- Create: `network-k8s/modules/vpc/{main,variables,outputs}.tf`

- [ ] **Step 1: Copiar conteúdo do plano original (Plan 1 antigo, Task 4)**

Reusar exatamente o módulo VPC do plano original infra-k8s — sem mudanças. Variables, main (data AZ + VPC + IGW + subnets pub/priv + NAT + RT), outputs (vpc_id, public_subnet_ids, private_subnet_ids).

- [ ] **Step 2: Commit**

```bash
git add network-k8s/modules/vpc/
git commit -m "feat(network-k8s/vpc): VPC + 2 public + 2 private subnets + IGW + NAT"
git push
```

---

## Task 4: `network-k8s/modules/eks/` + `lambda-sg.tf`

**Files:**
- Create: `network-k8s/modules/eks/{main,variables,outputs}.tf`
- Create: `network-k8s/lambda-sg.tf`

- [ ] **Step 1: Módulo EKS — copiar do plano original**

EKS cluster v1.30 + node group + OIDC provider. `aws_iam_role.LabRole` (Academy). Outputs: cluster_name, cluster_endpoint, cluster_ca, cluster_sg_id, oidc_provider_arn, oidc_provider_url.

- [ ] **Step 2: `network-k8s/lambda-sg.tf`**

```hcl
resource "aws_security_group" "lambda" {
  name        = "${var.cluster_name}-lambda-sg"
  description = "SG for Lambda functions"
  vpc_id      = module.vpc.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.cluster_name}-lambda-sg" }
}
```

- [ ] **Step 3: Commit**

```bash
git add network-k8s/modules/eks/ network-k8s/lambda-sg.tf
git commit -m "feat(network-k8s): EKS cluster + node group + OIDC + Lambda SG"
git push
```

---

## Task 5: `network-k8s/main.tf` + `outputs.tf` — primeira aplicação (VPC + EKS)

**Files:**
- Create: `network-k8s/main.tf`, `network-k8s/outputs.tf`

- [ ] **Step 1: `network-k8s/main.tf`**

```hcl
module "vpc" {
  source       = "./modules/vpc"
  cluster_name = var.cluster_name
  vpc_cidr     = var.vpc_cidr
}

module "eks" {
  source             = "./modules/eks"
  cluster_name       = var.cluster_name
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  public_subnet_ids  = module.vpc.public_subnet_ids
  node_instance_type = var.node_instance_type
  node_desired_size  = var.node_desired_size
  node_min_size      = var.node_min_size
  node_max_size      = var.node_max_size
}
```

- [ ] **Step 2: `network-k8s/outputs.tf`**

```hcl
output "vpc_id"              { value = module.vpc.vpc_id }
output "private_subnet_ids"  { value = module.vpc.private_subnet_ids }
output "public_subnet_ids"   { value = module.vpc.public_subnet_ids }
output "eks_cluster_name"    { value = module.eks.cluster_name }
output "eks_cluster_endpoint"{ value = module.eks.cluster_endpoint }
output "eks_cluster_sg_id"   { value = module.eks.cluster_sg_id }
output "oidc_provider_arn"   { value = module.eks.oidc_provider_arn }
output "oidc_provider_url"   { value = module.eks.oidc_provider_url }
output "lambda_sg_id"        { value = aws_security_group.lambda.id }
```

- [ ] **Step 3: Init + primeiro apply (cria VPC + EKS, ~15min)**

```bash
cd network-k8s
terraform init
terraform plan -out=plan-vpc-eks.tfplan
terraform apply plan-vpc-eks.tfplan
cd ..
```

- [ ] **Step 4: Validar cluster up**

```bash
aws eks update-kubeconfig --name auto-repair-shop-cluster --region us-east-1
kubectl get nodes
```

Expected: 2 nodes Ready.

- [ ] **Step 5: Commit**

```bash
git add network-k8s/main.tf network-k8s/outputs.tf
git commit -m "feat(network-k8s): wire VPC+EKS modules and apply"
git push
```

---

## Task 6: `network-k8s/modules/alb-controller/`

**Files:**
- Create: `network-k8s/modules/alb-controller/{main,variables}.tf`

- [ ] **Step 1: Copiar conteúdo do plano original (Plan 1 antigo, Task 8)**

IAM policy (do GitHub aws-load-balancer-controller v2.7.2), IAM role com trust policy OIDC, k8s ServiceAccount com annotation IRSA, Helm release `aws-load-balancer-controller` v1.7.2.

- [ ] **Step 2: Wire-up no main.tf + apply**

```hcl
module "alb_controller" {
  source             = "./modules/alb-controller"
  cluster_name       = module.eks.cluster_name
  oidc_provider_arn  = module.eks.oidc_provider_arn
  oidc_provider_url  = module.eks.oidc_provider_url
  aws_region         = var.aws_region
  vpc_id             = module.vpc.vpc_id
}
```

```bash
cd network-k8s
terraform init
terraform apply
cd ..
kubectl get deployment -n kube-system aws-load-balancer-controller
```

Expected: 2/2 Ready.

- [ ] **Step 3: Commit**

```bash
git add network-k8s/modules/alb-controller/ network-k8s/main.tf
git commit -m "feat(network-k8s): install AWS Load Balancer Controller via IRSA + Helm"
git push
```

---

## Task 7: `network-k8s/modules/namespaces/` + observability

**Files:**
- Create: `network-k8s/modules/namespaces/main.tf`
- Create: `network-k8s/modules/observability/{main,variables}.tf`
- Create: `network-k8s/helm-values/{kube-prometheus-stack,loki,tempo,otel-collector,blackbox-exporter}.yaml`

- [ ] **Step 1: Namespaces module + wire-up + apply (igual ao plano original)**

Cria `auto-repair-shop-hml`, `auto-repair-shop-prod`, `observability`.

- [ ] **Step 2: Helm values (copiar do plano original Tasks 10-11)**

5 arquivos YAML em `network-k8s/helm-values/`.

- [ ] **Step 3: Observability module — 5 Helm releases**

`helm_release` para: kube-prometheus-stack 61.3.2, loki-stack 2.10.2, tempo 1.10.1, opentelemetry-collector 0.97.1, prometheus-blackbox-exporter 8.17.0. `depends_on` encadeado.

- [ ] **Step 4: Wire-up + apply**

```hcl
module "namespaces" {
  source = "./modules/namespaces"
  depends_on = [module.eks]
}

module "observability" {
  source     = "./modules/observability"
  values_dir = "${path.module}/helm-values"
  depends_on = [module.namespaces, module.alb_controller]
}
```

```bash
cd network-k8s
terraform apply
cd ..
kubectl get pods -n observability
```

Expected: prometheus, grafana, alertmanager, loki, tempo, otel-collector, blackbox-exporter — todos Running.

- [ ] **Step 5: Commit**

```bash
git add network-k8s/modules/namespaces/ network-k8s/modules/observability/ network-k8s/helm-values/ network-k8s/main.tf
git commit -m "feat(network-k8s): namespaces and full observability stack (Prom+Loki+Tempo+OTel+Blackbox)"
git push
```

---

## Task 8: `network-k8s/irsa-app-sns.tf` + `ssm-outputs.tf`

**Files:**
- Create: `network-k8s/irsa-app-sns.tf`, `network-k8s/ssm-outputs.tf`

- [ ] **Step 1: IRSA app pod publicar SNS (copiar do plano original Task 12)**

Cria IAM role + policy + k8s ServiceAccount em cada namespace (hml/prod) com annotation IRSA.

- [ ] **Step 2: SSM outputs**

```hcl
locals {
  ssm_params = {
    "/auto-repair-shop/eks/cluster-name"           = module.eks.cluster_name
    "/auto-repair-shop/eks/cluster-endpoint"       = module.eks.cluster_endpoint
    "/auto-repair-shop/eks/oidc-provider-arn"      = module.eks.oidc_provider_arn
    "/auto-repair-shop/network/vpc-id"             = module.vpc.vpc_id
    "/auto-repair-shop/network/private-subnet-ids" = join(",", module.vpc.private_subnet_ids)
    "/auto-repair-shop/network/lambda-sg-id"       = aws_security_group.lambda.id
    "/auto-repair-shop/network/eks-sg-id"          = module.eks.cluster_sg_id
  }
}

resource "aws_ssm_parameter" "outputs" {
  for_each  = local.ssm_params
  name      = each.key
  type      = "String"
  value     = each.value
  overwrite = true
}
```

- [ ] **Step 3: Apply + validar + commit**

```bash
cd network-k8s && terraform apply && cd ..
aws ssm get-parameters --names /auto-repair-shop/eks/cluster-name /auto-repair-shop/network/vpc-id --query "Parameters[].[Name,Value]" --output table

git add network-k8s/irsa-app-sns.tf network-k8s/ssm-outputs.tf
git commit -m "feat(network-k8s): IRSA for app pods + SSM outputs"
git push
```

**Checkpoint: network-k8s completo. Validar Grafana acessível via LoadBalancer hostname antes de seguir.**

---

## Task 9: `database/` — backend, providers, variables

**Files:**
- Create: `database/{backend,providers,variables}.tf`

- [ ] **Step 1: `database/backend.tf`**

```hcl
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws        = { source = "hashicorp/aws",       version = "~> 5.60" }
    postgresql = { source = "cyrilgdn/postgresql", version = "~> 1.23" }
  }
  backend "s3" {
    bucket  = "auto-repair-shop-tfstate-<ACCOUNT_ID>"
    key     = "infra/database/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
```

- [ ] **Step 2: `database/providers.tf`**

```hcl
provider "aws" { region = var.aws_region }

provider "postgresql" {
  host            = module.rds.endpoint
  port            = module.rds.port
  database        = "postgres"
  username        = module.rds.username
  password        = var.db_master_password
  sslmode         = "require"
  connect_timeout = 15
}
```

- [ ] **Step 3: `database/variables.tf`**

```hcl
variable "aws_region"           { type = string default = "us-east-1" }
variable "db_identifier"        { type = string default = "auto-repair-shop-db" }
variable "db_instance_class"    { type = string default = "db.t3.micro" }
variable "db_allocated_storage" { type = number default = 20 }
variable "db_engine_version"    { type = string default = "16.3" }
variable "db_master_username"   { type = string default = "postgres" }
variable "db_master_password"   { type = string sensitive = true }
variable "db_password_hml"      { type = string sensitive = true }
variable "db_password_prod"     { type = string sensitive = true }
variable "runner_cidr"          { type = string default = "0.0.0.0/0" }
```

- [ ] **Step 4: Substituir ACCOUNT_ID + commit**

```bash
sed -i.bak "s/<ACCOUNT_ID>/$ACCOUNT_ID/" database/backend.tf && rm database/backend.tf.bak
git add database/{backend,providers,variables}.tf
git commit -m "feat(database): backend, providers, variables"
git push
```

---

## Task 10: `database/data.tf` + `database/modules/rds/`

**Files:**
- Create: `database/data.tf`
- Create: `database/modules/rds/{main,variables,outputs}.tf`

- [ ] **Step 1: `database/data.tf` — lê network-k8s state**

```hcl
data "aws_caller_identity" "current" {}

data "terraform_remote_state" "network_k8s" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/network-k8s/terraform.tfstate"
    region = var.aws_region
  }
}

locals {
  vpc_id             = data.terraform_remote_state.network_k8s.outputs.vpc_id
  private_subnet_ids = data.terraform_remote_state.network_k8s.outputs.private_subnet_ids
  public_subnet_ids  = data.terraform_remote_state.network_k8s.outputs.public_subnet_ids
  eks_sg_id          = data.terraform_remote_state.network_k8s.outputs.eks_cluster_sg_id
  lambda_sg_id       = data.terraform_remote_state.network_k8s.outputs.lambda_sg_id
}
```

- [ ] **Step 2: Módulo RDS — copiar do plano original (Plan 2 antigo, Task 5)**

DB subnet group, security group (ingress de EKS SG, Lambda SG, runner CIDR), parameter group, RDS PostgreSQL 16, `publicly_accessible = true` (compromisso pra postgresql provider).

- [ ] **Step 3: Commit**

```bash
git add database/data.tf database/modules/rds/
git commit -m "feat(database): RDS module + remote_state read of network-k8s"
git push
```

---

## Task 11: `database/main.tf` + primeira aplicação RDS

**Files:**
- Create: `database/main.tf`, `database/outputs.tf`

- [ ] **Step 1: `database/main.tf`**

```hcl
module "rds" {
  source = "./modules/rds"
  db_identifier        = var.db_identifier
  db_instance_class    = var.db_instance_class
  db_allocated_storage = var.db_allocated_storage
  db_engine_version    = var.db_engine_version
  db_master_username   = var.db_master_username
  db_master_password   = var.db_master_password
  vpc_id               = local.vpc_id
  private_subnet_ids   = local.private_subnet_ids
  public_subnet_ids    = local.public_subnet_ids
  eks_sg_id            = local.eks_sg_id
  lambda_sg_id         = local.lambda_sg_id
  runner_cidr          = var.runner_cidr
}
```

- [ ] **Step 2: `database/outputs.tf`** (provisório)

```hcl
output "rds_endpoint"   { value = module.rds.endpoint }
output "rds_port"       { value = module.rds.port }
output "rds_identifier" { value = module.rds.identifier }
output "rds_sg_id"      { value = module.rds.sg_id }
```

- [ ] **Step 3: tfvars temporário + apply (~10min)**

```bash
cd database
cat > terraform.tfvars <<EOF
db_master_password = "$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-25)"
db_password_hml    = "$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-25)"
db_password_prod   = "$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-25)"
EOF
# IMPORTANTE: salvar esses valores num gerenciador — viram GitHub Secrets na Task 14
terraform init
terraform apply
cd ..
```

- [ ] **Step 4: Validar conexão**

```bash
ENDPOINT=$(cd database && terraform output -raw rds_endpoint)
PWD=$(grep db_master_password database/terraform.tfvars | cut -d'"' -f2)
PGPASSWORD="$PWD" psql -h "$ENDPOINT" -U postgres -d postgres -c "SELECT version();"
```

- [ ] **Step 5: Commit**

```bash
git add database/main.tf database/outputs.tf
git commit -m "feat(database): provision RDS PostgreSQL 16"
git push
```

---

## Task 12: `database/databases.tf` + `database/users.tf`

**Files:**
- Create: `database/databases.tf`, `database/users.tf`

- [ ] **Step 1: Copiar `databases.tf` e `users.tf` do plano original (Plan 2 antigo, Tasks 7-8)**

- [ ] **Step 2: Apply + validar isolamento**

```bash
cd database
terraform apply
cd ..

ENDPOINT=$(cd database && terraform output -raw rds_endpoint)
PWD_HML=$(grep db_password_hml database/terraform.tfvars | cut -d'"' -f2)
PGPASSWORD="$PWD_HML" psql -h "$ENDPOINT" -U app_hml -d auto_repair_shop_hml -c "SELECT current_user;"
# app_hml NÃO deve conseguir conectar ao prod
PGPASSWORD="$PWD_HML" psql -h "$ENDPOINT" -U app_hml -d auto_repair_shop_prod -c "SELECT 1;" || echo "isolamento OK"
```

- [ ] **Step 3: Commit**

```bash
git add database/databases.tf database/users.tf
git commit -m "feat(database): create 2 databases (hml/prod) and isolated app users"
git push
```

---

## Task 13: `database/secrets.tf` + `database/ssm-outputs.tf`

**Files:**
- Create: `database/secrets.tf`, `database/ssm-outputs.tf`

- [ ] **Step 1: Copiar `secrets.tf` e `ssm-outputs.tf` do plano original (Plan 2 antigo, Tasks 9-10)**

Adicionar ao `outputs.tf` os ARN dos secrets pra remote_state.

- [ ] **Step 2: Apply + validar**

```bash
cd database && terraform apply && cd ..
aws secretsmanager get-secret-value --secret-id auto-repair-shop/db-password-hml --query SecretString --output text | jq
```

- [ ] **Step 3: Commit**

```bash
git add database/secrets.tf database/ssm-outputs.tf database/outputs.tf
git commit -m "feat(database): Secrets Manager containers + SSM outputs"
git push
```

**Checkpoint: database completo. Ambos sub-projetos funcionais.**

---

## Task 14: GitHub Secrets — registrar valores

- [ ] **Step 1: Setar todos os secrets**

```bash
MASTER=$(grep db_master_password database/terraform.tfvars | cut -d'"' -f2)
HML=$(grep db_password_hml      database/terraform.tfvars | cut -d'"' -f2)
PROD=$(grep db_password_prod     database/terraform.tfvars | cut -d'"' -f2)

gh secret set DB_MASTER_PASSWORD --body "$MASTER" --repo ivanzao/auto-repair-shop-infra
gh secret set DB_PASSWORD_HML    --body "$HML"    --repo ivanzao/auto-repair-shop-infra
gh secret set DB_PASSWORD_PROD   --body "$PROD"   --repo ivanzao/auto-repair-shop-infra

gh secret set AWS_ACCESS_KEY_ID     --body "$AWS_ACCESS_KEY_ID"     --repo ivanzao/auto-repair-shop-infra
gh secret set AWS_SECRET_ACCESS_KEY --body "$AWS_SECRET_ACCESS_KEY" --repo ivanzao/auto-repair-shop-infra
gh secret set AWS_SESSION_TOKEN     --body "$AWS_SESSION_TOKEN"     --repo ivanzao/auto-repair-shop-infra

rm database/terraform.tfvars  # já está no .gitignore mas pra garantir
```

---

## Task 15: `.github/workflows/pr-check.yaml` com path-filter

**Files:**
- Create: `.github/workflows/pr-check.yaml`

- [ ] **Step 1: Criar workflow com matrix**

```yaml
name: PR Check

on:
  pull_request:
    branches: [main]

jobs:
  detect:
    runs-on: ubuntu-latest
    outputs:
      network: ${{ steps.f.outputs.network }}
      database: ${{ steps.f.outputs.database }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: f
        with:
          filters: |
            network:
              - 'network-k8s/**'
            database:
              - 'database/**'

  network-k8s:
    needs: detect
    if: needs.detect.outputs.network == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: network-k8s
        run: |
          terraform fmt -check -recursive
          terraform init
          terraform validate
          terraform plan -no-color | tee plan.txt
      - uses: actions/github-script@v7
        with:
          script: |
            const plan = require('fs').readFileSync('network-k8s/plan.txt','utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '## network-k8s plan\n```hcl\n' + plan.slice(0, 50000) + '\n```'
            });

  database:
    needs: detect
    if: needs.detect.outputs.database == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: database
        env:
          TF_VAR_db_master_password: ${{ secrets.DB_MASTER_PASSWORD }}
          TF_VAR_db_password_hml:    ${{ secrets.DB_PASSWORD_HML }}
          TF_VAR_db_password_prod:   ${{ secrets.DB_PASSWORD_PROD }}
        run: |
          terraform fmt -check -recursive
          terraform init
          terraform validate
          terraform plan -no-color | tee plan.txt
      - uses: actions/github-script@v7
        with:
          script: |
            const plan = require('fs').readFileSync('database/plan.txt','utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '## database plan\n```hcl\n' + plan.slice(0, 50000) + '\n```'
            });
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/pr-check.yaml
git commit -m "ci: PR check with path-filter for network-k8s and database sub-projects"
git push
```

---

## Task 16: `.github/workflows/deploy.yaml` com path-filter

**Files:**
- Create: `.github/workflows/deploy.yaml`

- [ ] **Step 1: Criar workflow**

```yaml
name: Deploy

on:
  push:
    branches: [main]
  workflow_dispatch: {}

jobs:
  detect:
    runs-on: ubuntu-latest
    outputs:
      network:  ${{ steps.f.outputs.network }}
      database: ${{ steps.f.outputs.database }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: f
        with:
          filters: |
            network:
              - 'network-k8s/**'
            database:
              - 'database/**'

  network-k8s:
    needs: detect
    if: needs.detect.outputs.network == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: network-k8s
        run: |
          terraform init
          terraform apply -auto-approve

  database:
    needs: [detect, network-k8s]
    if: |
      always() &&
      needs.detect.outputs.database == 'true' &&
      (needs.network-k8s.result == 'success' || needs.network-k8s.result == 'skipped')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: database
        env:
          TF_VAR_db_master_password: ${{ secrets.DB_MASTER_PASSWORD }}
          TF_VAR_db_password_hml:    ${{ secrets.DB_PASSWORD_HML }}
          TF_VAR_db_password_prod:   ${{ secrets.DB_PASSWORD_PROD }}
        run: |
          terraform init
          terraform apply -auto-approve
```

- [ ] **Step 2: Branch protection**

```bash
gh api -X PUT \
  repos/ivanzao/auto-repair-shop-infra/branches/main/protection \
  -F required_status_checks[strict]=true \
  -F required_status_checks[contexts][]=network-k8s \
  -F required_status_checks[contexts][]=database \
  -F enforce_admins=true \
  -F required_pull_request_reviews[required_approving_review_count]=0 \
  -f restrictions=null \
  -F allow_force_pushes=false \
  -F allow_deletions=false
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy.yaml
git commit -m "ci: deploy with path-filter; database depends on network-k8s"
git push
```

---

## Task 17: README.md

**Files:**
- Create: `README.md`

- [ ] **Step 1: Criar README**

````markdown
# auto-repair-shop-infra

Terraform monorepo provisioning **all infrastructure** for the Auto Repair Shop platform: VPC, EKS, observability stack, and managed PostgreSQL database. Organized as 2 sub-projects with isolated Terraform state files.

## Sub-projects

| Folder | Responsibility |
|--------|----------------|
| [`network-k8s/`](./network-k8s/) | VPC, EKS, AWS LB Controller, observability (Helm), namespaces, IRSA, Lambda SG |
| [`database/`](./database/) | RDS PostgreSQL 16, 2 databases (hml/prod), 2 app users with isolated grants, Secrets Manager |

Each sub-project has its own state file in S3 (`infra/network-k8s/terraform.tfstate` and `infra/database/terraform.tfstate`) under the same bucket. Database reads VPC/SG outputs from network-k8s via `terraform_remote_state`.

## Why monorepo with sub-projects (instead of 2 separate repos)

- Reduces backend / providers / CI / secrets duplication
- Cross-cutting changes (e.g., adding VPC endpoint for RDS) are atomic in a single PR
- Path-filter in CI ensures only changed sub-projects re-apply
- State isolation preserves blast radius

See [ADR-012](https://github.com/ivanzao/auto-repair-shop/tree/main/docs/architecture/adrs/ADR-012-infra-monorepo-with-subprojects.md).

## Setup inicial

```bash
./scripts/bootstrap-state-bucket.sh        # cria bucket S3 (uma vez)
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s/<ACCOUNT_ID>/$ACCOUNT_ID/" network-k8s/backend.tf database/backend.tf

cd network-k8s && terraform init && terraform apply && cd ..
cd database     && terraform init && terraform apply && cd ..  # depende do network-k8s
```

## CI/CD

- **pr-check.yaml** — `terraform fmt/validate/plan` por sub-projeto que mudou
- **deploy.yaml** — `terraform apply` por sub-projeto que mudou; `database` espera `network-k8s` em pushes que tocam ambos

## Outputs (SSM)

| Param | Conteúdo |
|-------|----------|
| `/auto-repair-shop/eks/cluster-name` | nome do cluster |
| `/auto-repair-shop/network/vpc-id` | ID da VPC |
| `/auto-repair-shop/network/lambda-sg-id` | SG das Lambdas |
| `/auto-repair-shop/db/endpoint` | endpoint RDS |
| `/auto-repair-shop/{env}/db/secret-arn` | ARN do Secrets Manager por env |

## Documentação arquitetural

Veja [docs/architecture/](https://github.com/ivanzao/auto-repair-shop/tree/main/docs/architecture/) no repo principal.
````

- [ ] **Step 2: Commit final**

```bash
git add README.md
git commit -m "docs: README with monorepo rationale and setup instructions"
git push
```

---

## Critérios de conclusão deste plano

- [ ] `kubectl get nodes` — 2 nodes Ready
- [ ] `kubectl get ns` — `auto-repair-shop-hml`, `auto-repair-shop-prod`, `observability`
- [ ] `kubectl get pods -n observability` — todos Running
- [ ] `kubectl get sa -n auto-repair-shop-hml auto-repair-shop` tem annotation IRSA
- [ ] `aws rds describe-db-instances --db-instance-identifier auto-repair-shop-db` status `available`
- [ ] `psql -U app_hml -d auto_repair_shop_hml` conecta; `-d auto_repair_shop_prod` falha (isolamento)
- [ ] SSM params publicados (5+ valores)
- [ ] Grafana acessível via LoadBalancer hostname
- [ ] Pipeline `deploy.yaml` testado com PR pequeno em cada sub-projeto
- [ ] Path-filter funciona (push só em `network-k8s/` não dispara `database` job)
- [ ] Branch `main` protegida; PR obrigatório
- [ ] `soat-architecture` adicionado como collaborator
