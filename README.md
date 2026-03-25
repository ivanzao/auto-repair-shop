# Auto Repair Shop

Sistema de gerenciamento para oficina mecanica desenvolvido em Kotlin com arquitetura hexagonal (ports & adapters) multi-modulo.

---

## Estrutura de Pastas

```
auto-repair-shop/
├── main/                  # Aplicacao principal (entry point, DI)
├── domain/                # Logica de negocio (modelos, use cases, ports)
├── api/                   # REST API (Ktor routes, DTOs)
├── storage/               # Persistencia (Exposed, Flyway migrations)
├── worker/                # Background jobs (EventBus, CommandBus)
├── jwt/                   # Autenticacao JWT
├── email/                 # Integracao MailerSend
├── infra/
│   ├── k8s/               # Manifestos Kubernetes
│   ├── terraform/         # Terraform (AWS EKS, RDS, VPC)
│   └── load-test/         # Teste de carga (K6)
├── .github/workflows/     # CI/CD Pipeline
├── Dockerfile             # Multi-stage build (JDK + JRE)
├── docker-compose.yaml    # Orquestracao local (app + PostgreSQL)
└── build.gradle.kts       # Build principal
```

---

## Stack

- **Linguagem**: Kotlin 2.2.10
- **JVM**: Java 21
- **Build**: Gradle 8.14 (Kotlin DSL)
- **Web Framework**: Ktor 3.3.3
- **Dependency Injection**: Koin 4.1.1
- **Database**: PostgreSQL 18.1
- **ORM**: Exposed 0.61.0
- **Migrations**: Flyway
- **Testing**: JUnit 5, MockK, TestContainers
- **Quality**: JaCoCo, SonarQube
- **Infra**: Docker, Kubernetes, Terraform, GitHub Actions

---

## Execucao Local

### Opcao 1: Docker Compose (Recomendado)

**Pre-requisitos:** Docker, Docker Compose e Java 21

```bash
# Build do JAR
./gradlew :main:shadowJar

# Build e start dos containers
docker-compose up --build -d

# Verificar logs
docker-compose logs -f app

# Parar
docker-compose down
```

Acesse:
- API: http://localhost:8080/v1
- Swagger UI: http://localhost:8080/swagger
- Health Check: http://localhost:8080/health

### Opcao 2: Execucao Local (sem Docker)

**Pre-requisitos:** Java 21, PostgreSQL rodando localmente

```bash
# Build
./gradlew build

# Executar
./gradlew :main:run
```

### Banco de dados local (Docker Compose)

```
Host: localhost
Port: 5432
Database: auto-repair-shop
Username: app
Password: test
```

---

## Dockerfile Multi-stage

| Stage | Uso | Descricao |
|-------|-----|-----------|
| `build` | CI | Compila o JAR com Gradle (JDK 21) |
| `runtime` | Base interna | JRE 21 + usuario non-root (base para production e dev) |
| `production` | CI/CD, EKS | runtime + JAR do build stage |
| `dev` | docker-compose local | runtime + JAR pre-compilado localmente |

---

## Testes

```bash
# Testes unitarios
./gradlew test

# Testes de integracao (requer Docker para TestContainers)
./gradlew integrationTest

# Cobertura de codigo (JaCoCo)
./gradlew jacocoAggregatedReport
# Relatorio em: build/reports/jacoco/jacocoAggregatedReport/html/index.html
```

---

## Load Test (K6)

**Pre-requisito:** [K6](https://k6.io/) instalado

```bash
k6 run --env K6_BASE_URL=<LOAD_BALANCER_URL> infra/load-test/k6-stress-test.js
```

Stages: warm-up 5 VUs (30s) → ramp-up 15 VUs (1m) → stress 25 VUs (2m) → sustain 25 VUs (3m) → cool-down (1m)

---

## Deploy em Kubernetes

### Usando manifestos diretamente

```bash
# Aplicar todos os manifestos
kubectl apply -f infra/k8s/

# Verificar pods
kubectl get pods -n auto-repair-shop

# Verificar servicos
kubectl get svc -n auto-repair-shop

# Ver logs da aplicacao
kubectl logs -f deployment/auto-repair-shop -n auto-repair-shop
```

**Importante:** Os secrets (DB, JWT, MailerSend) sao criados automaticamente pelo CI/CD a partir dos GitHub Secrets. Nao ha arquivo de secrets versionado no repositorio.

### Arquivos K8s

| Arquivo | Descricao |
|---------|-----------|
| `namespace.yaml` | Namespace `auto-repair-shop` |
| `configmap.yaml` | Configuracoes nao-sensiveis |
| `deployment.yaml` | App (2 replicas, health probes) |
| `service.yaml` | LoadBalancer na porta 8080 |
| `hpa.yaml` | HPA 2-4 replicas (CPU 70%) |
| `metrics-server.yaml` | Metrics Server (necessario para HPA) |

---

## Infraestrutura Provisionada — Terraform (AWS)

### Estrutura de Modulos

```
infra/terraform/
├── main.tf              # Orquestra os modulos
├── variables.tf         # Variaveis do root
├── outputs.tf           # Outputs do root
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

### Recursos Provisionados

| Modulo | Recursos |
|--------|----------|
| **vpc** | VPC, 2 subnets publicas, 2 subnets privadas, Internet Gateway, NAT Gateway, route tables |
| **eks** | EKS cluster (v1.35), managed node group (t3.small, 2-3 nodes) |
| **rds** | PostgreSQL 16 (db.t3.micro), DB subnet group, security group |

### Pre-requisitos

- [Terraform](https://www.terraform.io/downloads) >= 1.5.0
- [AWS CLI](https://aws.amazon.com/cli/) configurado com credenciais
- [kubectl](https://kubernetes.io/docs/tasks/tools/)

### Variaveis

| Variavel | Descricao | Default |
|----------|-----------|---------|
| `aws_region` | Regiao AWS | `us-east-1` |
| `cluster_name` | Nome do cluster EKS | `auto-repair-shop-cluster` |
| `db_password` | Senha do PostgreSQL (sensivel) | — |
| `public_access_cidrs` | CIDRs com acesso ao API server EKS | `["0.0.0.0/0"]` |
| `node_instance_type` | Tipo da instancia EC2 | `t3.small` |

### Como Aplicar

```bash
cd infra/terraform/

terraform init
terraform plan
terraform apply

# Configurar kubectl
aws eks update-kubeconfig --name auto-repair-shop-cluster --region us-east-1
```

### Como Destruir

```bash
terraform destroy
```

### Outputs

| Output | Descricao |
|--------|-----------|
| `cluster_endpoint` | Endpoint do cluster EKS |
| `cluster_name` | Nome do cluster EKS |
| `rds_endpoint` | Endpoint do PostgreSQL RDS |

---

## CI/CD Pipeline

### `pr-check.yaml` — PRs para main

- Testes unitarios e de integracao

### `build-and-deploy.yaml` — Push para main

| Job | Descricao |
|-----|-----------|
| `test` | Testes unitarios e de integracao |
| `build` | Build e push da imagem Docker para GHCR |
| `terraform` | Provisionamento da infra AWS (S3 state, EKS, RDS) |
| `deploy` | Aplica manifestos K8s, cria secrets, patch configmap (RDS endpoint + LB hostname), atualiza imagem, smoke test |

### Secrets necessarios no GitHub

| Secret | Descricao |
|--------|-----------|
| `AWS_ACCESS_KEY_ID` | Chave de acesso AWS |
| `AWS_SECRET_ACCESS_KEY` | Chave secreta AWS |
| `AWS_SESSION_TOKEN` | Token de sessao AWS |
| `DB_PASSWORD` | Senha do PostgreSQL (RDS) |
| `JWT_SECRET` | Secret para assinatura JWT |
| `MAILERSEND_TOKEN` | Token da API MailerSend |
| `GHCR_PAT` | Personal Access Token para GHCR |

> `AWS_REGION` e `RDS_ENDPOINT` nao sao secrets — regiao esta hardcoded no workflow e o RDS endpoint vem do output do Terraform.

---

## API

- **Base path**: `/v1`
- **Swagger UI**: [`/swagger`](http://localhost:8080/swagger)
- **Health check**: `/health`
- **Autenticacao**: JWT Bearer tokens (roles: ADMIN e ATTENDANT)