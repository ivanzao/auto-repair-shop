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
├── worker/                # Background jobs (EventBus, CommandBus, SNS relay)
├── infra/
│   ├── k8s/               # Kustomize (base/ + overlays/{hml,prod}/)
│   └── load-test/         # Teste de carga (K6)
├── .github/workflows/     # CI/CD Pipeline
├── Dockerfile             # Multi-stage build (JDK + JRE)
├── docker-compose.yaml    # Orquestracao local (app + PostgreSQL)
└── build.gradle.kts       # Build principal
```

> **Infraestrutura AWS e plataforma K8s** (VPC, EKS, RDS, ALB Controller, Prometheus Operator, OTel Operator, Alloy/Loki/Tempo, ServiceAccount IRSA, Namespace) vivem no repositório separado [`auto-repair-shop-infra`](../auto-repair-shop-infra/). Este repo só carrega manifestos app-específicos (Deployment, Service, ConfigMap, HPA, ServiceMonitor) via Kustomize. O contrato com o infra é por **convenções K8s** (nomes de namespace/SA, CRDs do OTel Operator e Prometheus Operator) — sem referência direta a recursos AWS.

---

## Arquitetura

### Authentication

O app **não valida assinatura JWT** nem armazena credenciais. Toda autenticação é delegada ao **API Gateway + Lambda Authorizer** (em outro repo). O app recebe o `Authorization: Bearer <jwt>` já validado pelo API Gateway, decodifica as claims (`sub` → `userId`, `role`) e confia.

- Rotas protegidas usam `authenticate("admin")` ou `authenticate("attendant")` (Ktor) com `JwtBearerAuthenticationProvider`.
- Sem `Authorization: Bearer <jwt>` → `401 Unauthorized`.
- Role inválida para o endpoint → `403 Forbidden`.

### Event Outbox → SNS

Eventos de domínio marcados como `external = true` (ex.: `QuoteEmailRequestedEvent`) são gravados na tabela `events` (outbox) dentro da mesma transação que gera o efeito. O `EventProcessorTask` (scheduler periódico) lê o outbox e o `SnsRelayEventHandler` publica o payload no SNS. O Lambda de envio de email consome do SQS subscrito ao tópico.

```
[OrderListenerUseCase.sendQuoteApprovalEmail]
        │
        ▼
[events table] ──(EventProcessorTask)──▶ [SnsRelayEventHandler] ──▶ SNS
                                                                     │
                                                                     ▼
                                                                   SQS ──▶ Lambda (MailerSend)
```

Eventos `external = false` (ex.: `OrderCompletedEvent`) seguem o fluxo in-memory via `EventBus`.

### Observability

- **Metrics**: Micrometer + Prometheus em `/metrics`. ServiceMonitor (Prometheus Operator instalado pelo infra) faz scrape a cada 30s.
- **Logs**: JSON estruturado via logstash-logback-encoder. Inclui `traceId`/`spanId`/`requestId` do MDC. O Alloy daemonset (instalado pelo infra) coleta e manda pro Loki.
- **Tracing**: auto-injetado pelo OpenTelemetry Operator (instalado pelo infra). O Deployment do app traz a annotation `instrumentation.opentelemetry.io/inject-java: "true"` que ativa o injection do agent Java. Traces vão pro Tempo via Alloy.
- **Counters de negócio**: `orders_created_total`.

---

## Stack

- **Linguagem**: Kotlin 2.2.10
- **JVM**: Java 21
- **Build**: Gradle 8.14 (Kotlin DSL)
- **Web Framework**: Ktor 3.3.3
- **DI**: Koin 4.1.1
- **Database**: PostgreSQL 18.1
- **ORM**: Exposed 0.61.0
- **Migrations**: Flyway
- **Messaging**: AWS SDK for Kotlin (SNS)
- **Observability**: Micrometer Prometheus, logstash-logback-encoder
- **Testing**: JUnit 5, MockK, TestContainers
- **Quality**: JaCoCo, SonarQube
- **Infra (app-side)**: Docker, Kustomize, GitHub Actions

---

## Execucao Local

### Opcao 1: Docker Compose (Recomendado)

```bash
./gradlew :main:shadowJar
docker-compose up --build -d
docker-compose logs -f app
```

Acesse:
- API: http://localhost:8080/v1
- Swagger UI: http://localhost:8080/swagger
- Health: http://localhost:8080/health
- Metrics: http://localhost:8080/metrics

### Opcao 2: Execucao Local (sem Docker)

```bash
./gradlew build
./gradlew :main:run
```

### Banco local

```
Host: localhost   Port: 5432   Database: auto-repair-shop
Username: app     Password: test
```

---

## Testes

```bash
./gradlew test                    # Unitarios
./gradlew integrationTest         # Requer Docker (TestContainers Postgres + LocalStack SNS/SQS)
./gradlew jacocoAggregatedReport  # Relatorio em build/reports/jacoco/...
```

---

## Load Test (K6)

```bash
k6 run --env K6_BASE_URL=<API_GW_URL> infra/load-test/k6-stress-test.js
```

---

## Deploy em Kubernetes (Kustomize)

```
infra/k8s/
├── base/
│   ├── deployment.yaml         # Container, probes, envFrom
│   ├── service.yaml            # NLB privado (interno)
│   ├── configmap.yaml          # SERVER_PORT, etc.
│   ├── hpa.yaml                # CPU 70% (min/max definidos no overlay)
│   ├── servicemonitor.yaml     # Prometheus scrape de /metrics
│   ├── serviceaccount.yaml     # IRSA annotation injetada no overlay
│   └── kustomization.yaml
└── overlays/
    ├── hml/{kustomization,deployment-patch,configmap-patch,serviceaccount-patch}.yaml
    └── prod/{kustomization,deployment-patch,configmap-patch,serviceaccount-patch}.yaml
```

### Aplicar manualmente

```bash
kubectl kustomize infra/k8s/overlays/hml | kubectl apply -f -
```

### Valores dinâmicos por env

O CI lê 4 params do SSM, busca credenciais do DB no Secrets Manager (JSON com host/port/dbname/username/password) e patcheia o ConfigMap antes do apply:

| Param SSM | Conteúdo | Uso |
|-----------|----------|-----|
| `/auto-repair-shop/{env}/eks/cluster-name` | nome do cluster EKS | `aws eks update-kubeconfig` |
| `/auto-repair-shop/{env}/db/secret-arn` | ARN do Secrets Manager com credenciais do app | `aws secretsmanager get-secret-value` → JSON com host, port, dbname, username, password |
| `/auto-repair-shop/{env}/sns/events-topic-arn` | ARN do tópico SNS de eventos | `SNS_TOPIC_ARN` no ConfigMap |
| `/auto-repair-shop/{env}/apigw/endpoint` | endpoint do API Gateway | smoke test pós-deploy |

Todos os 4 params são **obrigatórios** — se algum não existir, o deploy falha no step de leitura SSM. Os dois primeiros são publicados hoje em `hml/ssm.tf`/`prod/ssm.tf` no `auto-repair-shop-infra`. SNS e APIGW serão publicados pelos sub-projetos correspondentes (Plans de Lambda + API Gateway).

---

## CI/CD Pipeline

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `pr-check.yaml` | PRs para `main`/`develop` | Unit + integration tests + Kustomize lint |
| `build-and-deploy.yaml` | Push `main` (prod) ou `develop` (hml) | Test → Build (Docker → GHCR) → Deploy (Kustomize + SSM) |

### Secrets necessarios no GitHub

| Secret | Descricao |
|--------|-----------|
| `AWS_ACCESS_KEY_ID` | Chave de acesso AWS |
| `AWS_SECRET_ACCESS_KEY` | Chave secreta AWS |
| `AWS_SESSION_TOKEN` | Token de sessao AWS |
| `GHCR_PAT` | Personal Access Token para GHCR |

Tudo mais (DB endpoint, SNS ARN, API GW endpoint) vem do SSM. A senha do DB vem do Secrets Manager (ARN lido do SSM).

---

## API

- **Base path**: `/v1`
- **Swagger UI**: [`/swagger`](http://localhost:8080/swagger)
- **Health check**: `/health`
- **Metrics**: `/metrics`
- **Autenticacao**: `Authorization: Bearer <jwt>` (validado pelo Lambda Authorizer no API Gateway; app decoda claims sem re-validar assinatura)
- **Attendants**: gerenciamento via `/v1/attendants` (CRUD restrito a `ADMIN`); provisionamento de credenciais é responsabilidade do Lambda/Cognito
