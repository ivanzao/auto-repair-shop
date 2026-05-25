# Plan: infra — vpc / k8s / db (3 sub-projetos Terraform)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refatorar o repositório `auto-repair-shop-infra` de 2 para 3 sub-projetos Terraform com state separado: `vpc/` (VPC + Lambda SG), `k8s/` (EKS + ALB Controller + observability + IRSA) e `db/` (RDS PostgreSQL 16 + databases + users + secrets). `k8s/` lê outputs de `vpc/` via `terraform_remote_state`; `db/` lê outputs de `vpc/` e de `k8s/`. SSM Parameter Store é usado apenas para valores que **aplicações** consomem, não como mecanismo de compartilhamento entre sub-projetos.

**Architecture:** O repo já contém `network-k8s/` e `database/` com código funcional mas ainda **não aplicado** (backend.tf tem `<ACCOUNT_ID>` como placeholder). O plano remove essas pastas e cria a nova estrutura de 3 sub-projetos. Dependências: `vpc` → `k8s` (lê vpc state) → `db` (lê vpc + k8s state). CI/CD com path-filter garante que só o sub-projeto que mudou é re-aplicado.

**Tech Stack:** Terraform 1.9+, AWS provider ~5.60, Helm ~2.13, Kubernetes ~2.30, cyrilgdn/postgresql ~1.23.

**Context:** Código existente em `network-k8s/` e `database/` já está correto — este plano o reorganiza, não o reescreve. Nenhum state Terraform existe ainda na AWS.

---

## Estrutura de arquivos resultante

```
auto-repair-shop-infra/
├── .github/workflows/
│   ├── pr-check.yaml
│   └── deploy.yaml
├── .gitignore
├── README.md
├── scripts/bootstrap-state-bucket.sh
├── vpc/
│   ├── backend.tf                 # key: infra/vpc/terraform.tfstate
│   ├── providers.tf               # somente aws provider
│   ├── variables.tf               # aws_region, cluster_name, vpc_cidr
│   ├── main.tf                    # module vpc + aws_security_group lambda
│   ├── outputs.tf                 # vpc_id, subnet_ids, lambda_sg_id
│   ├── ssm-outputs.tf             # SSM params para apps
│   └── modules/vpc/
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
├── k8s/
│   ├── backend.tf                 # key: infra/k8s/terraform.tfstate
│   ├── providers.tf               # aws + kubernetes + helm
│   ├── variables.tf               # aws_region, cluster_name, node_*
│   ├── data.tf                    # terraform_remote_state vpc/
│   ├── main.tf                    # modules: eks, alb-controller, namespaces, observability
│   ├── irsa-app-sns.tf            # IRSA + k8s ServiceAccount para app pods
│   ├── outputs.tf                 # eks_cluster_sg_id, oidc_*, cluster_name/endpoint
│   ├── ssm-outputs.tf             # SSM params para apps
│   ├── helm-values/
│   │   ├── kube-prometheus-stack.yaml
│   │   ├── loki.yaml
│   │   ├── tempo.yaml
│   │   ├── otel-collector.yaml
│   │   └── blackbox-exporter.yaml
│   └── modules/
│       ├── eks/{main,variables,outputs}.tf
│       ├── alb-controller/{main,variables}.tf
│       ├── namespaces/main.tf
│       └── observability/{main,variables}.tf
└── db/
    ├── backend.tf                 # key: infra/db/terraform.tfstate
    ├── providers.tf               # aws + postgresql
    ├── variables.tf
    ├── data.tf                    # terraform_remote_state vpc/ + k8s/
    ├── main.tf                    # module rds
    ├── databases.tf               # postgresql_database hml + prod
    ├── users.tf                   # postgresql_role + grants
    ├── secrets.tf                 # Secrets Manager
    ├── outputs.tf
    ├── ssm-outputs.tf             # SSM params para apps
    └── modules/rds/
        ├── main.tf
        ├── variables.tf
        └── outputs.tf
```

---

## Pré-requisitos

- AWS Academy session ativa (`aws sts get-caller-identity` retorna conta válida)
- `gh`, `aws`, `terraform`, `kubectl`, `psql` instalados
- S3 state bucket já criado via `scripts/bootstrap-state-bucket.sh`
- Repo `ivanzao/auto-repair-shop-infra` já existe no GitHub

---

## Task 1: Remover estrutura antiga e criar scaffold

**Files:**
- Delete: `network-k8s/` (todo o diretório)
- Delete: `database/` (todo o diretório)
- Create: estrutura de pastas dos 3 novos sub-projetos

- [ ] **Step 1: Remover diretórios antigos**

```bash
cd /home/ivanzao/dev/repository/auto-repair-shop-infra
git rm -r network-k8s/ database/
```

- [ ] **Step 2: Criar scaffold dos 3 sub-projetos**

```bash
mkdir -p vpc/modules/vpc
mkdir -p k8s/modules/{eks,alb-controller,namespaces,observability}
mkdir -p k8s/helm-values
mkdir -p db/modules/rds
```

- [ ] **Step 3: Verificar estrutura**

```bash
find vpc k8s db -type d | sort
```

Expected:
```
db
db/modules
db/modules/rds
k8s
k8s/helm-values
k8s/modules
k8s/modules/alb-controller
k8s/modules/eks
k8s/modules/namespaces
k8s/modules/observability
vpc
vpc/modules
vpc/modules/vpc
```

- [ ] **Step 4: Commit scaffold**

```bash
git add vpc/ k8s/ db/
git commit -m "refactor(infra): split network-k8s+database into vpc/k8s/db sub-projects"
git push
```

---

## Task 2: `vpc/` — backend, providers, variables, módulo VPC

**Files:**
- Create: `vpc/backend.tf`
- Create: `vpc/providers.tf`
- Create: `vpc/variables.tf`
- Create: `vpc/modules/vpc/main.tf`
- Create: `vpc/modules/vpc/variables.tf`
- Create: `vpc/modules/vpc/outputs.tf`

- [ ] **Step 1: `vpc/backend.tf`** (substituir ACCOUNT_ID no Step 5)

```hcl
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.60" }
  }
  backend "s3" {
    bucket  = "auto-repair-shop-tfstate-<ACCOUNT_ID>"
    key     = "infra/vpc/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
```

- [ ] **Step 2: `vpc/providers.tf`**

```hcl
provider "aws" { region = var.aws_region }
```

- [ ] **Step 3: `vpc/variables.tf`**

```hcl
variable "aws_region"   { type = string; default = "us-east-1" }
variable "cluster_name" { type = string; default = "auto-repair-shop-cluster" }
variable "vpc_cidr"     { type = string; default = "10.0.0.0/16" }
```

- [ ] **Step 4: `vpc/modules/vpc/variables.tf`**

```hcl
variable "cluster_name" {
  description = "Cluster name — usado para nomes de recursos e tags Kubernetes nas subnets"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block da VPC"
  type        = string
  default     = "10.0.0.0/16"
}
```

- [ ] **Step 5: `vpc/modules/vpc/main.tf`**

```hcl
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.cluster_name}-vpc"
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, 0)
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name                                        = "${var.cluster_name}-public"
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, 1)
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = true

  tags = {
    Name                                        = "${var.cluster_name}-public-b"
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_subnet" "private" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, 10)
  availability_zone = data.aws_availability_zones.available.names[0]

  tags = {
    Name                                        = "${var.cluster_name}-private"
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, 11)
  availability_zone = data.aws_availability_zones.available.names[1]

  tags = {
    Name                                        = "${var.cluster_name}-private-b"
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.cluster_name}-igw"
  }
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "${var.cluster_name}-nat-eip"
  }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public.id

  tags = {
    Name = "${var.cluster_name}-nat"
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${var.cluster_name}-public-rt"
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = {
    Name = "${var.cluster_name}-private-rt"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}
```

- [ ] **Step 6: `vpc/modules/vpc/outputs.tf`**

```hcl
output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = [aws_subnet.public.id, aws_subnet.public_b.id]
}

output "private_subnet_ids" {
  value = [aws_subnet.private.id, aws_subnet.private_b.id]
}
```

- [ ] **Step 7: Substituir ACCOUNT_ID**

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s/<ACCOUNT_ID>/$ACCOUNT_ID/" vpc/backend.tf
```

- [ ] **Step 8: Commit**

```bash
git add vpc/
git commit -m "feat(vpc): backend, providers, variables, VPC module"
git push
```

---

## Task 3: `vpc/main.tf` + `outputs.tf` + `ssm-outputs.tf` + apply

**Files:**
- Create: `vpc/main.tf`
- Create: `vpc/outputs.tf`
- Create: `vpc/ssm-outputs.tf`

- [ ] **Step 1: `vpc/main.tf`** — Lambda SG fica aqui porque é recurso de rede compartilhado

```hcl
module "vpc" {
  source       = "./modules/vpc"
  cluster_name = var.cluster_name
  vpc_cidr     = var.vpc_cidr
}

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

- [ ] **Step 2: `vpc/outputs.tf`**

```hcl
output "vpc_id"             { value = module.vpc.vpc_id }
output "public_subnet_ids"  { value = module.vpc.public_subnet_ids }
output "private_subnet_ids" { value = module.vpc.private_subnet_ids }
output "lambda_sg_id"       { value = aws_security_group.lambda.id }
```

- [ ] **Step 3: `vpc/ssm-outputs.tf`** — valores consumidos por aplicações (Lambda, scripts)

```hcl
locals {
  ssm_params = {
    "/auto-repair-shop/network/vpc-id"             = module.vpc.vpc_id
    "/auto-repair-shop/network/private-subnet-ids" = join(",", module.vpc.private_subnet_ids)
    "/auto-repair-shop/network/public-subnet-ids"  = join(",", module.vpc.public_subnet_ids)
    "/auto-repair-shop/network/lambda-sg-id"       = aws_security_group.lambda.id
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

- [ ] **Step 4: terraform init + apply (~5 min)**

```bash
cd vpc
terraform init
terraform plan -out=vpc.tfplan
terraform apply vpc.tfplan
cd ..
```

- [ ] **Step 5: Validar**

```bash
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=auto-repair-shop-cluster-vpc" \
  --query "Vpcs[0].{ID:VpcId,CIDR:CidrBlock}" --output table
aws ec2 describe-subnets \
  --filters "Name=tag:kubernetes.io/cluster/auto-repair-shop-cluster,Values=shared" \
  --query "Subnets[*].{ID:SubnetId,AZ:AvailabilityZone,Type:Tags[?Key=='Name']|[0].Value}" \
  --output table
```

Expected: 1 VPC + 4 subnets (2 public, 2 private) em 2 AZs.

- [ ] **Step 6: Commit**

```bash
git add vpc/
git commit -m "feat(vpc): main, outputs, ssm-outputs — VPC + Lambda SG aplicados"
git push
```

---

## Task 4: `k8s/` — backend, providers, variables, data, módulos

**Files:**
- Create: `k8s/backend.tf`
- Create: `k8s/providers.tf`
- Create: `k8s/variables.tf`
- Create: `k8s/data.tf`
- Create: `k8s/modules/eks/{main,variables,outputs}.tf`
- Create: `k8s/modules/alb-controller/{main,variables}.tf`
- Create: `k8s/modules/namespaces/main.tf`
- Create: `k8s/modules/observability/{main,variables}.tf`
- Create: `k8s/helm-values/*.yaml` (5 arquivos)

- [ ] **Step 1: `k8s/backend.tf`** (substituir ACCOUNT_ID no Step 6)

```hcl
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws        = { source = "hashicorp/aws",        version = "~> 5.60" }
    helm       = { source = "hashicorp/helm",       version = "~> 2.13" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.30" }
    tls        = { source = "hashicorp/tls",        version = "~> 4.0" }
  }
  backend "s3" {
    bucket  = "auto-repair-shop-tfstate-<ACCOUNT_ID>"
    key     = "infra/k8s/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
```

- [ ] **Step 2: `k8s/providers.tf`**

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

- [ ] **Step 3: `k8s/variables.tf`**

```hcl
variable "aws_region"         { type = string; default = "us-east-1" }
variable "cluster_name"       { type = string; default = "auto-repair-shop-cluster" }
variable "node_instance_type" { type = string; default = "t3.medium" }
variable "node_desired_size"  { type = number; default = 2 }
variable "node_min_size"      { type = number; default = 2 }
variable "node_max_size"      { type = number; default = 4 }
```

- [ ] **Step 4: `k8s/data.tf`** — lê state do vpc/ para obter IDs de rede

```hcl
data "aws_caller_identity" "current" {}

data "terraform_remote_state" "vpc" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/vpc/terraform.tfstate"
    region = var.aws_region
  }
}

locals {
  vpc_id             = data.terraform_remote_state.vpc.outputs.vpc_id
  private_subnet_ids = data.terraform_remote_state.vpc.outputs.private_subnet_ids
  public_subnet_ids  = data.terraform_remote_state.vpc.outputs.public_subnet_ids
}
```

- [ ] **Step 5: `k8s/modules/eks/main.tf`**

```hcl
data "aws_caller_identity" "current" {}

locals {
  lab_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/LabRole"
}

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

resource "aws_eks_node_group" "default" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-default"
  node_role_arn   = local.lab_role_arn
  subnet_ids      = var.private_subnet_ids
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
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

data "tls_certificate" "eks_oidc" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
}
```

- [ ] **Step 6: `k8s/modules/eks/variables.tf`**

```hcl
variable "cluster_name"       { type = string }
variable "vpc_id"             { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "public_subnet_ids"  { type = list(string) }
variable "node_instance_type" { type = string; default = "t3.medium" }
variable "node_desired_size"  { type = number; default = 2 }
variable "node_min_size"      { type = number; default = 2 }
variable "node_max_size"      { type = number; default = 4 }
variable "public_access_cidrs" { type = list(string); default = ["0.0.0.0/0"] }
```

- [ ] **Step 7: `k8s/modules/eks/outputs.tf`**

```hcl
output "cluster_name"     { value = aws_eks_cluster.main.name }
output "cluster_endpoint" { value = aws_eks_cluster.main.endpoint }
output "cluster_ca"       { value = aws_eks_cluster.main.certificate_authority[0].data }
output "cluster_sg_id"    { value = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id }
output "oidc_provider_arn" { value = aws_iam_openid_connect_provider.eks.arn }
output "oidc_provider_url" { value = replace(aws_eks_cluster.main.identity[0].oidc[0].issuer, "https://", "") }
```

- [ ] **Step 8: `k8s/modules/alb-controller/variables.tf`**

```hcl
variable "cluster_name"      { type = string }
variable "oidc_provider_arn" { type = string }
variable "oidc_provider_url" { type = string }
variable "aws_region"        { type = string; default = "us-east-1" }
variable "vpc_id"            { type = string }
```

- [ ] **Step 9: `k8s/modules/alb-controller/main.tf`**

```hcl
data "aws_caller_identity" "current" {}

locals {
  lab_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/LabRole"
  sa_name      = "aws-load-balancer-controller"
  namespace    = "kube-system"
}

data "aws_iam_policy_document" "alb_controller" {
  statement {
    effect  = "Allow"
    actions = ["iam:CreateServiceLinkedRole"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values   = ["elasticloadbalancing.amazonaws.com"]
    }
  }
  statement {
    effect  = "Allow"
    actions = [
      "ec2:DescribeAccountAttributes", "ec2:DescribeAddresses",
      "ec2:DescribeAvailabilityZones", "ec2:DescribeInternetGateways",
      "ec2:DescribeVpcs", "ec2:DescribeVpcPeeringConnections",
      "ec2:DescribeSubnets", "ec2:DescribeSecurityGroups",
      "ec2:DescribeInstances", "ec2:DescribeNetworkInterfaces",
      "ec2:DescribeTags", "ec2:GetCoipPoolUsage", "ec2:DescribeCoipPools",
      "elasticloadbalancing:DescribeLoadBalancers",
      "elasticloadbalancing:DescribeLoadBalancerAttributes",
      "elasticloadbalancing:DescribeListeners",
      "elasticloadbalancing:DescribeListenerCertificates",
      "elasticloadbalancing:DescribeSSLPolicies",
      "elasticloadbalancing:DescribeRules",
      "elasticloadbalancing:DescribeTargetGroups",
      "elasticloadbalancing:DescribeTargetGroupAttributes",
      "elasticloadbalancing:DescribeTargetHealth",
      "elasticloadbalancing:DescribeTags",
    ]
    resources = ["*"]
  }
  statement {
    effect  = "Allow"
    actions = [
      "cognito-idp:DescribeUserPoolClient", "acm:ListCertificates",
      "acm:DescribeCertificate", "iam:ListServerCertificates",
      "iam:GetServerCertificate", "waf-regional:GetWebACL",
      "waf-regional:GetWebACLForResource", "waf-regional:AssociateWebACL",
      "waf-regional:DisassociateWebACL", "wafv2:GetWebACL",
      "wafv2:GetWebACLForResource", "wafv2:AssociateWebACL",
      "wafv2:DisassociateWebACL", "shield:GetSubscriptionState",
      "shield:DescribeProtection", "shield:CreateProtection",
      "shield:DeleteProtection",
    ]
    resources = ["*"]
  }
  statement {
    effect  = "Allow"
    actions = ["ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress", "ec2:CreateSecurityGroup"]
    resources = ["*"]
  }
  statement {
    effect    = "Allow"
    actions   = ["ec2:CreateTags"]
    resources = ["arn:aws:ec2:*:*:security-group/*"]
    condition {
      test     = "StringEquals"
      variable = "ec2:CreateAction"
      values   = ["CreateSecurityGroup"]
    }
    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect  = "Allow"
    actions = ["ec2:CreateTags", "ec2:DeleteTags"]
    resources = ["arn:aws:ec2:*:*:security-group/*"]
    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["true"]
    }
    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect  = "Allow"
    actions = ["ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress", "ec2:DeleteSecurityGroup"]
    resources = ["*"]
    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect  = "Allow"
    actions = ["elasticloadbalancing:CreateLoadBalancer", "elasticloadbalancing:CreateTargetGroup"]
    resources = ["*"]
    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect  = "Allow"
    actions = [
      "elasticloadbalancing:CreateListener", "elasticloadbalancing:DeleteListener",
      "elasticloadbalancing:CreateRule", "elasticloadbalancing:DeleteRule",
    ]
    resources = ["*"]
  }
  statement {
    effect  = "Allow"
    actions = ["elasticloadbalancing:AddTags", "elasticloadbalancing:RemoveTags"]
    resources = [
      "arn:aws:elasticloadbalancing:*:*:targetgroup/*/*",
      "arn:aws:elasticloadbalancing:*:*:loadbalancer/net/*/*",
      "arn:aws:elasticloadbalancing:*:*:loadbalancer/app/*/*",
    ]
    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["true"]
    }
    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect  = "Allow"
    actions = ["elasticloadbalancing:AddTags", "elasticloadbalancing:RemoveTags"]
    resources = [
      "arn:aws:elasticloadbalancing:*:*:listener/net/*/*/*",
      "arn:aws:elasticloadbalancing:*:*:listener/app/*/*/*",
      "arn:aws:elasticloadbalancing:*:*:listener-rule/net/*/*/*",
      "arn:aws:elasticloadbalancing:*:*:listener-rule/app/*/*/*",
    ]
  }
  statement {
    effect  = "Allow"
    actions = [
      "elasticloadbalancing:ModifyLoadBalancerAttributes",
      "elasticloadbalancing:SetIpAddressType", "elasticloadbalancing:SetSecurityGroups",
      "elasticloadbalancing:SetSubnets", "elasticloadbalancing:DeleteLoadBalancer",
      "elasticloadbalancing:ModifyTargetGroup", "elasticloadbalancing:ModifyTargetGroupAttributes",
      "elasticloadbalancing:DeleteTargetGroup",
    ]
    resources = ["*"]
    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect    = "Allow"
    actions   = ["elasticloadbalancing:AddTags"]
    resources = [
      "arn:aws:elasticloadbalancing:*:*:targetgroup/*/*",
      "arn:aws:elasticloadbalancing:*:*:loadbalancer/net/*/*",
      "arn:aws:elasticloadbalancing:*:*:loadbalancer/app/*/*",
    ]
    condition {
      test     = "StringEquals"
      variable = "elasticloadbalancing:CreateAction"
      values   = ["CreateTargetGroup", "CreateLoadBalancer"]
    }
    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }
  statement {
    effect  = "Allow"
    actions = ["elasticloadbalancing:RegisterTargets", "elasticloadbalancing:DeregisterTargets"]
    resources = ["arn:aws:elasticloadbalancing:*:*:targetgroup/*/*"]
  }
  statement {
    effect  = "Allow"
    actions = [
      "elasticloadbalancing:SetWebAcl", "elasticloadbalancing:ModifyListener",
      "elasticloadbalancing:AddListenerCertificates",
      "elasticloadbalancing:RemoveListenerCertificates", "elasticloadbalancing:ModifyRule",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "alb_controller" {
  name        = "${var.cluster_name}-alb-controller"
  description = "IAM policy for AWS Load Balancer Controller"
  policy      = data.aws_iam_policy_document.alb_controller.json
}

resource "aws_iam_role_policy_attachment" "alb_controller" {
  role       = "LabRole"
  policy_arn = aws_iam_policy.alb_controller.arn
}

resource "kubernetes_service_account" "alb_controller" {
  metadata {
    name      = local.sa_name
    namespace = local.namespace
    annotations = {
      "eks.amazonaws.com/role-arn" = local.lab_role_arn
    }
  }
}

resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.7.2"
  namespace  = local.namespace

  set { name = "clusterName";           value = var.cluster_name }
  set { name = "serviceAccount.create"; value = "false" }
  set { name = "serviceAccount.name";   value = local.sa_name }
  set { name = "region";                value = var.aws_region }
  set { name = "vpcId";                 value = var.vpc_id }

  depends_on = [
    kubernetes_service_account.alb_controller,
    aws_iam_role_policy_attachment.alb_controller,
  ]
}
```

- [ ] **Step 10: `k8s/modules/namespaces/main.tf`**

```hcl
resource "kubernetes_namespace" "hml" {
  metadata {
    name   = "auto-repair-shop-hml"
    labels = { environment = "hml", app = "auto-repair-shop" }
  }
}

resource "kubernetes_namespace" "prod" {
  metadata {
    name   = "auto-repair-shop-prod"
    labels = { environment = "prod", app = "auto-repair-shop" }
  }
}

resource "kubernetes_namespace" "observability" {
  metadata {
    name   = "observability"
    labels = { app = "observability" }
  }
}
```

- [ ] **Step 11: `k8s/modules/observability/variables.tf`**

```hcl
variable "values_dir" {
  description = "Caminho para o diretório com os arquivos de valores Helm"
  type        = string
}
```

- [ ] **Step 12: `k8s/modules/observability/main.tf`**

```hcl
resource "helm_release" "kube_prometheus_stack" {
  name             = "kube-prometheus-stack"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  version          = "61.3.2"
  namespace        = "observability"
  create_namespace = false
  timeout          = 600

  values = [file("${var.values_dir}/kube-prometheus-stack.yaml")]
}

resource "helm_release" "loki" {
  name             = "loki"
  repository       = "https://grafana.github.io/helm-charts"
  chart            = "loki-stack"
  version          = "2.10.2"
  namespace        = "observability"
  create_namespace = false
  timeout          = 300

  values     = [file("${var.values_dir}/loki.yaml")]
  depends_on = [helm_release.kube_prometheus_stack]
}

resource "helm_release" "tempo" {
  name             = "tempo"
  repository       = "https://grafana.github.io/helm-charts"
  chart            = "tempo"
  version          = "1.10.1"
  namespace        = "observability"
  create_namespace = false
  timeout          = 300

  values     = [file("${var.values_dir}/tempo.yaml")]
  depends_on = [helm_release.loki]
}

resource "helm_release" "otel_collector" {
  name             = "otel-collector"
  repository       = "https://open-telemetry.github.io/opentelemetry-helm-charts"
  chart            = "opentelemetry-collector"
  version          = "0.97.1"
  namespace        = "observability"
  create_namespace = false
  timeout          = 300

  values     = [file("${var.values_dir}/otel-collector.yaml")]
  depends_on = [helm_release.tempo]
}

resource "helm_release" "blackbox_exporter" {
  name             = "blackbox-exporter"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "prometheus-blackbox-exporter"
  version          = "8.17.0"
  namespace        = "observability"
  create_namespace = false
  timeout          = 300

  values     = [file("${var.values_dir}/blackbox-exporter.yaml")]
  depends_on = [helm_release.otel_collector]
}
```

- [ ] **Step 13: Helm values — 5 arquivos em `k8s/helm-values/`**

`k8s/helm-values/kube-prometheus-stack.yaml`:
```yaml
grafana:
  enabled: true
  service:
    type: LoadBalancer
  adminPassword: "admin"
  persistence:
    enabled: false

prometheus:
  prometheusSpec:
    retention: 15d
    storageSpec: {}
    serviceMonitorSelectorNilUsesHelmValues: false
    podMonitorSelectorNilUsesHelmValues: false

alertmanager:
  enabled: true

nodeExporter:
  enabled: true

kubeStateMetrics:
  enabled: true
```

`k8s/helm-values/loki.yaml`:
```yaml
loki:
  enabled: true
  persistence:
    enabled: false
  config:
    auth_enabled: false
    ingester:
      chunk_idle_period: 3m
      chunk_block_size: 262144
      chunk_retain_period: 1m
    schema_config:
      configs:
        - from: "2024-01-01"
          store: boltdb-shipper
          object_store: filesystem
          schema: v11
          index:
            prefix: index_
            period: 24h

promtail:
  enabled: true
  config:
    clients:
      - url: http://loki:3100/loki/api/v1/push
```

`k8s/helm-values/tempo.yaml`:
```yaml
tempo:
  storage:
    trace:
      backend: local
      local:
        path: /var/tempo/traces
  retention: 24h

persistence:
  enabled: false

service:
  type: ClusterIP
```

`k8s/helm-values/otel-collector.yaml`:
```yaml
mode: deployment

config:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

  processors:
    batch: {}
    memory_limiter:
      check_interval: 1s
      limit_percentage: 75
      spike_limit_percentage: 15

  exporters:
    otlp:
      endpoint: tempo:4317
      tls:
        insecure: true
    prometheusremotewrite:
      endpoint: http://kube-prometheus-stack-prometheus:9090/api/v1/write
    loki:
      endpoint: http://loki:3100/loki/api/v1/push

  service:
    pipelines:
      traces:
        receivers: [otlp]
        processors: [memory_limiter, batch]
        exporters: [otlp]
      metrics:
        receivers: [otlp]
        processors: [memory_limiter, batch]
        exporters: [prometheusremotewrite]
      logs:
        receivers: [otlp]
        processors: [memory_limiter, batch]
        exporters: [loki]

ports:
  otlp:
    enabled: true
  otlp-http:
    enabled: true
```

`k8s/helm-values/blackbox-exporter.yaml`:
```yaml
config:
  modules:
    http_2xx:
      prober: http
      timeout: 5s
      http:
        valid_http_versions: ["HTTP/1.1", "HTTP/2.0"]
        valid_status_codes: []
        method: GET
        follow_redirects: true
        preferred_ip_protocol: ip4

serviceMonitor:
  enabled: true
  defaults:
    labels:
      release: kube-prometheus-stack
  targets:
    - name: auto-repair-shop-hml
      url: http://auto-repair-shop.auto-repair-shop-hml.svc.cluster.local/health
      interval: 30s
      scrapeTimeout: 10s
    - name: auto-repair-shop-prod
      url: http://auto-repair-shop.auto-repair-shop-prod.svc.cluster.local/health
      interval: 30s
      scrapeTimeout: 10s
```

- [ ] **Step 14: Substituir ACCOUNT_ID**

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s/<ACCOUNT_ID>/$ACCOUNT_ID/" k8s/backend.tf
```

- [ ] **Step 15: Commit**

```bash
git add k8s/
git commit -m "feat(k8s): backend, providers, variables, data, todos os módulos e helm-values"
git push
```

---

## Task 5: `k8s/main.tf` + `irsa-app-sns.tf` + `outputs.tf` + `ssm-outputs.tf` + apply

**Files:**
- Create: `k8s/main.tf`
- Create: `k8s/irsa-app-sns.tf`
- Create: `k8s/outputs.tf`
- Create: `k8s/ssm-outputs.tf`

- [ ] **Step 1: `k8s/main.tf`**

```hcl
module "eks" {
  source             = "./modules/eks"
  cluster_name       = var.cluster_name
  vpc_id             = local.vpc_id
  private_subnet_ids = local.private_subnet_ids
  public_subnet_ids  = local.public_subnet_ids
  node_instance_type = var.node_instance_type
  node_desired_size  = var.node_desired_size
  node_min_size      = var.node_min_size
  node_max_size      = var.node_max_size
}

module "alb_controller" {
  source            = "./modules/alb-controller"
  cluster_name      = module.eks.cluster_name
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider_url = module.eks.oidc_provider_url
  aws_region        = var.aws_region
  vpc_id            = local.vpc_id
}

module "namespaces" {
  source     = "./modules/namespaces"
  depends_on = [module.eks]
}

module "observability" {
  source     = "./modules/observability"
  values_dir = "${path.module}/helm-values"
  depends_on = [module.namespaces, module.alb_controller]
}
```

- [ ] **Step 2: `k8s/irsa-app-sns.tf`**

```hcl
data "aws_caller_identity" "irsa" {}

locals {
  lab_role_arn_irsa = "arn:aws:iam::${data.aws_caller_identity.irsa.account_id}:role/LabRole"
  app_namespaces    = ["auto-repair-shop-hml", "auto-repair-shop-prod"]
}

resource "aws_iam_policy" "app_sns" {
  name        = "${var.cluster_name}-app-sns"
  description = "Allow app pods to publish to SNS"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sns:Publish", "sns:ListTopics", "sns:GetTopicAttributes"]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "app_sns" {
  role       = "LabRole"
  policy_arn = aws_iam_policy.app_sns.arn
}

resource "kubernetes_service_account" "app" {
  for_each = toset(local.app_namespaces)

  metadata {
    name      = "auto-repair-shop"
    namespace = each.key
    annotations = {
      "eks.amazonaws.com/role-arn" = local.lab_role_arn_irsa
    }
  }

  depends_on = [module.namespaces]
}
```

- [ ] **Step 3: `k8s/outputs.tf`**

```hcl
output "eks_cluster_name"     { value = module.eks.cluster_name }
output "eks_cluster_endpoint" { value = module.eks.cluster_endpoint }
output "eks_cluster_ca"       { value = module.eks.cluster_ca }
output "eks_cluster_sg_id"    { value = module.eks.cluster_sg_id }
output "oidc_provider_arn"    { value = module.eks.oidc_provider_arn }
output "oidc_provider_url"    { value = module.eks.oidc_provider_url }
```

- [ ] **Step 4: `k8s/ssm-outputs.tf`** — valores consumidos por aplicações

```hcl
locals {
  k8s_ssm_params = {
    "/auto-repair-shop/eks/cluster-name"      = module.eks.cluster_name
    "/auto-repair-shop/eks/cluster-endpoint"  = module.eks.cluster_endpoint
    "/auto-repair-shop/eks/oidc-provider-arn" = module.eks.oidc_provider_arn
    "/auto-repair-shop/network/eks-sg-id"     = module.eks.cluster_sg_id
  }
}

resource "aws_ssm_parameter" "k8s_outputs" {
  for_each  = local.k8s_ssm_params
  name      = each.key
  type      = "String"
  value     = each.value
  overwrite = true
}
```

- [ ] **Step 5: terraform init + apply (~15 min)**

```bash
cd k8s
terraform init
terraform plan -out=k8s.tfplan
terraform apply k8s.tfplan
cd ..
```

- [ ] **Step 6: Validar cluster up**

```bash
aws eks update-kubeconfig --name auto-repair-shop-cluster --region us-east-1
kubectl get nodes
kubectl get ns
kubectl get deployment -n kube-system aws-load-balancer-controller
kubectl get pods -n observability
```

Expected: 2 nodes Ready; namespaces `auto-repair-shop-hml`, `auto-repair-shop-prod`, `observability`; ALB controller 2/2; todos os pods observability Running.

- [ ] **Step 7: Commit**

```bash
git add k8s/
git commit -m "feat(k8s): main, irsa, outputs, ssm-outputs — EKS + observability aplicados"
git push
```

---

## Task 6: `db/` — backend, providers, variables, data, módulo RDS

**Files:**
- Create: `db/backend.tf`
- Create: `db/providers.tf`
- Create: `db/variables.tf`
- Create: `db/data.tf`
- Create: `db/modules/rds/{main,variables,outputs}.tf`

- [ ] **Step 1: `db/backend.tf`** (substituir ACCOUNT_ID no Step 6)

```hcl
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws        = { source = "hashicorp/aws",       version = "~> 5.60" }
    postgresql = { source = "cyrilgdn/postgresql", version = "~> 1.23" }
  }
  backend "s3" {
    bucket  = "auto-repair-shop-tfstate-<ACCOUNT_ID>"
    key     = "infra/db/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
```

- [ ] **Step 2: `db/providers.tf`**

```hcl
provider "aws" { region = var.aws_region }

provider "postgresql" {
  host            = module.rds.endpoint
  port            = module.rds.port
  database        = "postgres"
  username        = var.db_master_username
  password        = var.db_master_password
  sslmode         = "require"
  connect_timeout = 15
}
```

- [ ] **Step 3: `db/variables.tf`**

```hcl
variable "aws_region"           { type = string;  default = "us-east-1" }
variable "db_identifier"        { type = string;  default = "auto-repair-shop-db" }
variable "db_instance_class"    { type = string;  default = "db.t3.micro" }
variable "db_allocated_storage" { type = number;  default = 20 }
variable "db_engine_version"    { type = string;  default = "16.3" }
variable "db_master_username"   { type = string;  default = "postgres" }
variable "db_master_password"   { type = string;  sensitive = true }
variable "db_password_hml"      { type = string;  sensitive = true }
variable "db_password_prod"     { type = string;  sensitive = true }
variable "runner_cidr"          { type = string;  default = "0.0.0.0/0" }
```

- [ ] **Step 4: `db/data.tf`** — lê vpc/ para rede e k8s/ para eks_sg_id

```hcl
data "aws_caller_identity" "current" {}

data "terraform_remote_state" "vpc" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/vpc/terraform.tfstate"
    region = var.aws_region
  }
}

data "terraform_remote_state" "k8s" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/k8s/terraform.tfstate"
    region = var.aws_region
  }
}

locals {
  vpc_id             = data.terraform_remote_state.vpc.outputs.vpc_id
  private_subnet_ids = data.terraform_remote_state.vpc.outputs.private_subnet_ids
  public_subnet_ids  = data.terraform_remote_state.vpc.outputs.public_subnet_ids
  lambda_sg_id       = data.terraform_remote_state.vpc.outputs.lambda_sg_id
  eks_sg_id          = data.terraform_remote_state.k8s.outputs.eks_cluster_sg_id
}
```

- [ ] **Step 5: `db/modules/rds/variables.tf`**

```hcl
variable "db_identifier"        { type = string }
variable "db_instance_class"    { type = string; default = "db.t3.micro" }
variable "db_allocated_storage" { type = number; default = 20 }
variable "db_engine_version"    { type = string; default = "16.3" }
variable "db_master_username"   { type = string; default = "postgres" }
variable "db_master_password"   { type = string; sensitive = true }
variable "vpc_id"               { type = string }
variable "private_subnet_ids"   { type = list(string) }
variable "public_subnet_ids"    { type = list(string) }
variable "eks_sg_id"            { type = string }
variable "lambda_sg_id"         { type = string }
variable "runner_cidr"          { type = string; default = "0.0.0.0/0" }
```

- [ ] **Step 6: `db/modules/rds/main.tf`**

```hcl
resource "aws_db_subnet_group" "main" {
  name       = "${var.db_identifier}-subnet-group"
  subnet_ids = concat(var.private_subnet_ids, var.public_subnet_ids)

  tags = {
    Name = "${var.db_identifier}-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.db_identifier}-sg"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EKS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.eks_sg_id]
  }

  ingress {
    description     = "PostgreSQL from Lambda"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.lambda_sg_id]
  }

  ingress {
    description = "PostgreSQL from CI runner (Terraform postgresql provider)"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.runner_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.db_identifier}-sg"
  }
}

resource "aws_db_parameter_group" "postgres16" {
  name        = "${var.db_identifier}-pg16"
  family      = "postgres16"
  description = "Custom parameter group for PostgreSQL 16"

  parameter { name = "log_connections";    value = "1" }
  parameter { name = "log_disconnections"; value = "1" }
}

resource "aws_db_instance" "postgres" {
  identifier     = var.db_identifier
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = 100
  storage_type          = "gp2"

  db_name  = "postgres"
  username = var.db_master_username
  password = var.db_master_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres16.name

  publicly_accessible = true
  skip_final_snapshot = true
  multi_az            = false

  tags = {
    Name        = var.db_identifier
    Environment = "shared"
    Application = "auto-repair-shop"
  }
}
```

- [ ] **Step 7: `db/modules/rds/outputs.tf`**

```hcl
output "endpoint"   { value = split(":", aws_db_instance.postgres.endpoint)[0] }
output "port"       { value = aws_db_instance.postgres.port }
output "identifier" { value = aws_db_instance.postgres.identifier }
output "sg_id"      { value = aws_security_group.rds.id }
```

- [ ] **Step 8: Substituir ACCOUNT_ID**

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s/<ACCOUNT_ID>/$ACCOUNT_ID/" db/backend.tf
```

- [ ] **Step 9: Commit**

```bash
git add db/
git commit -m "feat(db): backend, providers, variables, data, módulo RDS"
git push
```

---

## Task 7: `db/main.tf` + demais arquivos + apply RDS

**Files:**
- Create: `db/main.tf`
- Create: `db/databases.tf`
- Create: `db/users.tf`
- Create: `db/secrets.tf`
- Create: `db/outputs.tf`
- Create: `db/ssm-outputs.tf`

- [ ] **Step 1: `db/main.tf`**

```hcl
module "rds" {
  source               = "./modules/rds"
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

- [ ] **Step 2: `db/databases.tf`**

```hcl
resource "postgresql_database" "hml" {
  name              = "auto_repair_shop_hml"
  owner             = "postgres"
  template          = "template0"
  lc_collate        = "en_US.UTF-8"
  lc_ctype          = "en_US.UTF-8"
  connection_limit  = -1
  allow_connections = true

  depends_on = [module.rds]
}

resource "postgresql_database" "prod" {
  name              = "auto_repair_shop_prod"
  owner             = "postgres"
  template          = "template0"
  lc_collate        = "en_US.UTF-8"
  lc_ctype          = "en_US.UTF-8"
  connection_limit  = -1
  allow_connections = true

  depends_on = [module.rds]
}
```

- [ ] **Step 3: `db/users.tf`**

```hcl
resource "postgresql_role" "app_hml" {
  name             = "app_hml"
  login            = true
  password         = var.db_password_hml
  connection_limit = -1

  depends_on = [postgresql_database.hml]
}

resource "postgresql_role" "app_prod" {
  name             = "app_prod"
  login            = true
  password         = var.db_password_prod
  connection_limit = -1

  depends_on = [postgresql_database.prod]
}

resource "postgresql_grant" "app_hml_connect" {
  database    = postgresql_database.hml.name
  role        = postgresql_role.app_hml.name
  object_type = "database"
  privileges  = ["CONNECT", "CREATE"]
}

resource "postgresql_grant" "app_prod_connect" {
  database    = postgresql_database.prod.name
  role        = postgresql_role.app_prod.name
  object_type = "database"
  privileges  = ["CONNECT", "CREATE"]
}

resource "postgresql_grant" "app_hml_schema" {
  database    = postgresql_database.hml.name
  role        = postgresql_role.app_hml.name
  schema      = "public"
  object_type = "schema"
  privileges  = ["USAGE", "CREATE"]
}

resource "postgresql_grant" "app_prod_schema" {
  database    = postgresql_database.prod.name
  role        = postgresql_role.app_prod.name
  schema      = "public"
  object_type = "schema"
  privileges  = ["USAGE", "CREATE"]
}
```

- [ ] **Step 4: `db/secrets.tf`**

```hcl
resource "aws_secretsmanager_secret" "db_master" {
  name                    = "auto-repair-shop/db-password-master"
  description             = "RDS master password"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db_master" {
  secret_id = aws_secretsmanager_secret.db_master.id
  secret_string = jsonencode({
    username = var.db_master_username
    password = var.db_master_password
    host     = module.rds.endpoint
    port     = module.rds.port
    dbname   = "postgres"
  })
}

resource "aws_secretsmanager_secret" "db_hml" {
  name                    = "auto-repair-shop/db-password-hml"
  description             = "RDS app_hml user password"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db_hml" {
  secret_id = aws_secretsmanager_secret.db_hml.id
  secret_string = jsonencode({
    username = "app_hml"
    password = var.db_password_hml
    host     = module.rds.endpoint
    port     = module.rds.port
    dbname   = "auto_repair_shop_hml"
  })

  depends_on = [postgresql_role.app_hml]
}

resource "aws_secretsmanager_secret" "db_prod" {
  name                    = "auto-repair-shop/db-password-prod"
  description             = "RDS app_prod user password"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db_prod" {
  secret_id = aws_secretsmanager_secret.db_prod.id
  secret_string = jsonencode({
    username = "app_prod"
    password = var.db_password_prod
    host     = module.rds.endpoint
    port     = module.rds.port
    dbname   = "auto_repair_shop_prod"
  })

  depends_on = [postgresql_role.app_prod]
}
```

- [ ] **Step 5: `db/outputs.tf`**

```hcl
output "rds_endpoint"      { value = module.rds.endpoint }
output "rds_port"          { value = module.rds.port }
output "rds_identifier"    { value = module.rds.identifier }
output "rds_sg_id"         { value = module.rds.sg_id }
output "secret_arn_hml"    { value = aws_secretsmanager_secret.db_hml.arn }
output "secret_arn_prod"   { value = aws_secretsmanager_secret.db_prod.arn }
output "secret_arn_master" { value = aws_secretsmanager_secret.db_master.arn }
```

- [ ] **Step 6: `db/ssm-outputs.tf`** — valores consumidos por aplicações

```hcl
locals {
  db_ssm_params = {
    "/auto-repair-shop/db/endpoint"          = module.rds.endpoint
    "/auto-repair-shop/db/port"              = tostring(module.rds.port)
    "/auto-repair-shop/db/identifier"        = module.rds.identifier
    "/auto-repair-shop/db/sg-id"             = module.rds.sg_id
    "/auto-repair-shop/hml/db/secret-arn"    = aws_secretsmanager_secret.db_hml.arn
    "/auto-repair-shop/prod/db/secret-arn"   = aws_secretsmanager_secret.db_prod.arn
    "/auto-repair-shop/db/master/secret-arn" = aws_secretsmanager_secret.db_master.arn
  }
}

resource "aws_ssm_parameter" "db_outputs" {
  for_each  = local.db_ssm_params
  name      = each.key
  type      = "String"
  value     = each.value
  overwrite = true
}
```

- [ ] **Step 7: tfvars temporário + apply (~10 min)**

```bash
cd db
cat > terraform.tfvars <<EOF
db_master_password = "$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-25)"
db_password_hml    = "$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-25)"
db_password_prod   = "$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-25)"
EOF
# IMPORTANTE: copiar esses valores agora — viram GitHub Secrets na Task 9
cat terraform.tfvars
terraform init
terraform apply
cd ..
```

- [ ] **Step 8: Validar RDS e isolamento de databases**

```bash
ENDPOINT=$(cd db && terraform output -raw rds_endpoint)
PWD_MASTER=$(grep db_master_password db/terraform.tfvars | cut -d'"' -f2)
PWD_HML=$(grep db_password_hml db/terraform.tfvars | cut -d'"' -f2)

# Conectar com master
PGPASSWORD="$PWD_MASTER" psql -h "$ENDPOINT" -U postgres -d postgres -c "SELECT version();"

# Conectar com app_hml no banco correto
PGPASSWORD="$PWD_HML" psql -h "$ENDPOINT" -U app_hml -d auto_repair_shop_hml -c "SELECT current_user;"

# app_hml NÃO deve conseguir conectar ao banco prod
PGPASSWORD="$PWD_HML" psql -h "$ENDPOINT" -U app_hml -d auto_repair_shop_prod -c "SELECT 1;" \
  || echo "isolamento OK — acesso negado ao banco prod"
```

- [ ] **Step 9: Commit**

```bash
git add db/
git commit -m "feat(db): main, databases, users, secrets, ssm-outputs — RDS aplicado"
git push
```

---

## Task 8: GitHub Secrets

- [ ] **Step 1: Setar todos os secrets no repositório**

```bash
MASTER=$(grep db_master_password db/terraform.tfvars | cut -d'"' -f2)
HML=$(grep db_password_hml      db/terraform.tfvars | cut -d'"' -f2)
PROD=$(grep db_password_prod     db/terraform.tfvars | cut -d'"' -f2)

gh secret set DB_MASTER_PASSWORD --body "$MASTER" --repo ivanzao/auto-repair-shop-infra
gh secret set DB_PASSWORD_HML    --body "$HML"    --repo ivanzao/auto-repair-shop-infra
gh secret set DB_PASSWORD_PROD   --body "$PROD"   --repo ivanzao/auto-repair-shop-infra

gh secret set AWS_ACCESS_KEY_ID     --body "$AWS_ACCESS_KEY_ID"     --repo ivanzao/auto-repair-shop-infra
gh secret set AWS_SECRET_ACCESS_KEY --body "$AWS_SECRET_ACCESS_KEY" --repo ivanzao/auto-repair-shop-infra
gh secret set AWS_SESSION_TOKEN     --body "$AWS_SESSION_TOKEN"     --repo ivanzao/auto-repair-shop-infra

# Remover tfvars local (já está no .gitignore, mas garantir)
rm db/terraform.tfvars
```

---

## Task 9: `.github/workflows/pr-check.yaml`

**Files:**
- Modify: `.github/workflows/pr-check.yaml`

- [ ] **Step 1: Reescrever com 3 sub-projetos**

```yaml
name: PR Check

on:
  pull_request:
    branches: [main]

jobs:
  detect:
    runs-on: ubuntu-latest
    outputs:
      vpc:      ${{ steps.f.outputs.vpc }}
      k8s:      ${{ steps.f.outputs.k8s }}
      database: ${{ steps.f.outputs.database }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: f
        with:
          filters: |
            vpc:
              - 'vpc/**'
            k8s:
              - 'k8s/**'
            database:
              - 'db/**'

  check-vpc:
    needs: detect
    if: needs.detect.outputs.vpc == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token:     ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: vpc
        run: |
          terraform fmt -check -recursive
          terraform init
          terraform validate
          terraform plan -no-color | tee plan.txt
      - uses: actions/github-script@v7
        with:
          script: |
            const plan = require('fs').readFileSync('vpc/plan.txt', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner, repo: context.repo.repo,
              body: '## vpc plan\n```hcl\n' + plan.slice(0, 50000) + '\n```'
            });

  check-k8s:
    needs: detect
    if: needs.detect.outputs.k8s == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token:     ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: k8s
        run: |
          terraform fmt -check -recursive
          terraform init
          terraform validate
          terraform plan -no-color | tee plan.txt
      - uses: actions/github-script@v7
        with:
          script: |
            const plan = require('fs').readFileSync('k8s/plan.txt', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner, repo: context.repo.repo,
              body: '## k8s plan\n```hcl\n' + plan.slice(0, 50000) + '\n```'
            });

  check-db:
    needs: detect
    if: needs.detect.outputs.database == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token:     ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: db
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
            const plan = require('fs').readFileSync('db/plan.txt', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner, repo: context.repo.repo,
              body: '## db plan\n```hcl\n' + plan.slice(0, 50000) + '\n```'
            });
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/pr-check.yaml
git commit -m "ci: PR check com path-filter para vpc, k8s e db"
git push
```

---

## Task 10: `.github/workflows/deploy.yaml`

**Files:**
- Modify: `.github/workflows/deploy.yaml`

- [ ] **Step 1: Reescrever com 3 sub-projetos em ordem vpc → k8s → db**

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
      vpc:      ${{ steps.f.outputs.vpc }}
      k8s:      ${{ steps.f.outputs.k8s }}
      database: ${{ steps.f.outputs.database }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: f
        with:
          filters: |
            vpc:
              - 'vpc/**'
            k8s:
              - 'k8s/**'
            database:
              - 'db/**'

  deploy-vpc:
    needs: detect
    if: needs.detect.outputs.vpc == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token:     ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: vpc
        run: |
          terraform init
          terraform apply -auto-approve

  deploy-k8s:
    needs: [detect, deploy-vpc]
    if: |
      always() &&
      needs.detect.outputs.k8s == 'true' &&
      (needs.deploy-vpc.result == 'success' || needs.deploy-vpc.result == 'skipped')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token:     ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: k8s
        run: |
          terraform init
          terraform apply -auto-approve

  deploy-db:
    needs: [detect, deploy-vpc, deploy-k8s]
    if: |
      always() &&
      needs.detect.outputs.database == 'true' &&
      (needs.deploy-vpc.result == 'success' || needs.deploy-vpc.result == 'skipped') &&
      (needs.deploy-k8s.result == 'success' || needs.deploy-k8s.result == 'skipped')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token:     ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
        with: { terraform_version: 1.9.5 }
      - working-directory: db
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
  -F required_status_checks[contexts][]=check-vpc \
  -F required_status_checks[contexts][]=check-k8s \
  -F required_status_checks[contexts][]=check-db \
  -F enforce_admins=true \
  -F required_pull_request_reviews[required_approving_review_count]=0 \
  -f restrictions=null \
  -F allow_force_pushes=false \
  -F allow_deletions=false
```

- [ ] **Step 3: Adicionar collaborator**

```bash
gh repo edit ivanzao/auto-repair-shop-infra --add-collaborator soat-architecture
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/deploy.yaml
git commit -m "ci: deploy vpc → k8s → db com path-filter e dependências corretas"
git push
```

---

## Task 11: README.md

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Atualizar README**

````markdown
# auto-repair-shop-infra

Terraform monorepo com **3 sub-projetos isolados** para toda a infraestrutura do Auto Repair Shop.

## Sub-projetos

| Pasta | Responsabilidade | Depende de |
|-------|-----------------|------------|
| [`vpc/`](./vpc/) | VPC, subnets, IGW, NAT, Lambda SG | — |
| [`k8s/`](./k8s/) | EKS, AWS LB Controller, namespaces, observability (Helm), IRSA | `vpc/` state |
| [`db/`](./db/) | RDS PostgreSQL 16, 2 databases (hml/prod), 2 app users, Secrets Manager | `vpc/` + `k8s/` state |

Cada sub-projeto tem seu próprio state file no S3 (`infra/vpc/`, `infra/k8s/`, `infra/db/`). Dependências entre sub-projetos são lidas via `terraform_remote_state`. SSM Parameter Store é usado somente para valores consumidos por aplicações (Lambda, scripts, outros repos).

## Por que 3 sub-projetos em vez de repositórios separados

- VPC é fundação compartilhada — k8s e db dependem dela, mas não um do outro diretamente
- Evita duplicação de backend / providers / CI / secrets
- Path-filter em CI garante que só o sub-projeto que mudou é re-aplicado
- State separado preserva blast radius: um `terraform destroy` em `db/` não afeta VPC ou EKS

## Setup inicial

```bash
./scripts/bootstrap-state-bucket.sh        # cria bucket S3 (uma vez)

# Substituir ACCOUNT_ID nos backends
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i "s/<ACCOUNT_ID>/$ACCOUNT_ID/" vpc/backend.tf k8s/backend.tf db/backend.tf

# Aplicar em ordem
cd vpc && terraform init && terraform apply && cd ..
cd k8s && terraform init && terraform apply && cd ..
cd db  && terraform init && terraform apply && cd ..
```

## CI/CD

- **pr-check.yaml** — `terraform fmt/validate/plan` por sub-projeto que mudou; posta resultado como comentário no PR
- **deploy.yaml** — `terraform apply` na ordem `vpc → k8s → db`; cada job só executa se o sub-projeto mudou e os anteriores tiveram sucesso (ou foram pulados)

## Outputs SSM (consumidos por aplicações)

| Param | Conteúdo |
|-------|----------|
| `/auto-repair-shop/network/vpc-id` | ID da VPC |
| `/auto-repair-shop/network/private-subnet-ids` | IDs das subnets privadas (CSV) |
| `/auto-repair-shop/network/lambda-sg-id` | SG das Lambdas |
| `/auto-repair-shop/network/eks-sg-id` | SG do cluster EKS |
| `/auto-repair-shop/eks/cluster-name` | nome do cluster EKS |
| `/auto-repair-shop/eks/cluster-endpoint` | endpoint da API EKS |
| `/auto-repair-shop/db/endpoint` | endpoint RDS |
| `/auto-repair-shop/{hml,prod}/db/secret-arn` | ARN do Secrets Manager por env |
````

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: README atualizado para estrutura de 3 sub-projetos"
git push
```

---

## Critérios de conclusão

- [ ] `kubectl get nodes` — 2 nodes Ready
- [ ] `kubectl get ns` — `auto-repair-shop-hml`, `auto-repair-shop-prod`, `observability` presentes
- [ ] `kubectl get pods -n observability` — prometheus, grafana, alertmanager, loki, tempo, otel-collector, blackbox-exporter todos Running
- [ ] `kubectl get sa -n auto-repair-shop-hml auto-repair-shop` tem annotation `eks.amazonaws.com/role-arn`
- [ ] `aws rds describe-db-instances --db-instance-identifier auto-repair-shop-db` — status `available`
- [ ] `psql -U app_hml -d auto_repair_shop_hml` conecta; `-d auto_repair_shop_prod` falha (isolamento)
- [ ] SSM params publicados: `/auto-repair-shop/network/vpc-id`, `/auto-repair-shop/eks/cluster-name`, `/auto-repair-shop/db/endpoint` + 4 outros
- [ ] Grafana acessível via LoadBalancer hostname
- [ ] Pipeline `deploy.yaml` testado: push só em `vpc/` não dispara `k8s` nem `db`
- [ ] Branch `main` protegida; PR obrigatório
- [ ] `soat-architecture` adicionado como collaborator
