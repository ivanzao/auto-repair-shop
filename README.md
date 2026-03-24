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
│   └── terraform/         # Terraform (AWS EKS, RDS, VPC)
├── .github/workflows/     # CI/CD Pipeline
├── Dockerfile             # Imagem de runtime (JRE + JAR)
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
| `hpa.yaml` | HPA 2-5 replicas (CPU 70%, memoria 80%) |

---

## Provisionamento com Terraform (AWS)

O Terraform gerencia apenas a infraestrutura (VPC, EKS, RDS). Os recursos da aplicacao sao gerenciados via `kubectl`.

```bash
cd infra/terraform/

# Inicializar
terraform init

# Planejar
terraform plan -var="db_password=YOUR_PASSWORD"

# Aplicar
terraform apply -var="db_password=YOUR_PASSWORD"

# Obter outputs
terraform output rds_endpoint
terraform output cluster_name

# Configurar kubectl
aws eks update-kubeconfig --name auto-repair-shop-cluster --region us-east-1
```

---

## CI/CD Pipeline

O pipeline GitHub Actions (`.github/workflows/ci-cd.yaml`) executa automaticamente:

1. **Build & Test** (em todo push/PR): build, testes unitarios, testes de integracao
2. **Docker** (apenas main): build da imagem e push para GitHub Container Registry
3. **Deploy** (apenas main): aplica manifestos K8s e atualiza a imagem no cluster

### Secrets necessarios no GitHub

| Secret | Descricao |
|--------|-----------|
| `AWS_ACCESS_KEY_ID` | Chave de acesso AWS |
| `AWS_SECRET_ACCESS_KEY` | Chave secreta AWS |
| `AWS_REGION` | Regiao AWS (ex: us-east-1) |
| `RDS_ENDPOINT` | Endpoint do RDS (obtido via `terraform output rds_endpoint`) |

---

## API

- **Base path**: `/v1`
- **Swagger UI**: [`/swagger`](http://localhost:8080/swagger)
- **Health check**: `/health`
- **Autenticacao**: JWT Bearer tokens (roles: ADMIN e ATTENDANT)