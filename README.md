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
├── worker/                # Background jobs (outbox → SNS relay, consumidor SQS de entrada, schedulers)
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

### Order service na Fase 4 (fluxo coreografado por eventos)

Na Fase 4 o monólito virou o **order service**. Ele é dono da OS, do cliente, do veículo e do catálogo de serviços; **estoque/peças** foram para o *execution* e **orçamento/pagamento** para o *billing*. O order não tem orquestrador central: **o estado da saga é derivado do status da OS**, movido por eventos.

- **Produz** `OrderCreated` ao abrir a OS.
- **Consome** de billing (`PaymentConfirmed`, `QuoteRejected`, `PaymentFailed`) e de execution (`ExecutionStarted`, `DiagnoseFinished`, `ExecutionFinished`, `PartsUnavailable`, `ExecutionFailed`, `ReservationExpired`).

**Saída (outbox → SNS):** `OrderCreated` é gravado na tabela `events` (outbox) na mesma transação da criação da OS. Um scheduler (`OutboxRelayTask` → `OutboxRelay`) publica o envelope no tópico `order-events` com o message attribute `eventType` (camelCase) + `traceparent`.

**Entrada (SQS → dispatch):** o `InboundEventConsumer` faz long-poll da fila de entrada, desserializa o envelope e o `InboundEventDispatcher` despacha por `eventType` lógico para os `InboundEventHandler`s. A fila recebe um superset (mesh): `eventType` sem handler é ignorado e marcado como processado. Idempotência por `eventId` na tabela `processed_events`.

```
[OrderUseCase.create] ──▶ [events (outbox)] ──(OutboxRelayTask)──▶ [OutboxRelay] ──▶ SNS order-events

SQS (fila de entrada) ──(InboundEventConsumer)──▶ [InboundEventDispatcher] ──▶ InboundEventHandler ──▶ OrderStatusUseCase
```

**Status derivado (remap):** o enum da OS é `RECEIVED, IN_PROGRESS, CANCELED, COMPLETED, DELIVERED`.

| Evento consumido | Efeito no status da OS |
|---|---|
| `PaymentConfirmed` | `RECEIVED → IN_PROGRESS` |
| `ExecutionStarted`, `DiagnoseFinished` | apenas observabilidade (sem transição) |
| `ExecutionFinished` | `IN_PROGRESS → COMPLETED` |
| `PartsUnavailable`, `QuoteRejected`, `PaymentFailed`, `ExecutionFailed`, `ReservationExpired` | `→ CANCELED` |
| entrega manual (REST) | `COMPLETED → DELIVERED` |

Todas as transições são idempotentes (evento repetido ou fora de ordem vira no-op).

### Observability

- **Metrics**: Micrometer + Prometheus em `/metrics`. ServiceMonitor (Prometheus Operator instalado pelo infra) faz scrape a cada 30s.
- **Logs**: JSON estruturado via logstash-logback-encoder. Inclui `traceId`/`spanId`/`requestId` do MDC. O Alloy daemonset (instalado pelo infra) coleta e manda pro Loki.
- **Tracing**: auto-injetado pelo OpenTelemetry Operator (instalado pelo infra). O Deployment do app traz a annotation `instrumentation.opentelemetry.io/inject-java: "true"` que ativa o injection do agent Java. Traces vão pro Tempo via Alloy.
- **Counters de negócio**, nomes como saem no `/metrics`: `orders_total`, `orders_by_status_total{status}`, `order_inbound_events_total`. O meter de criação se chama `orders_created_total` no código, mas `_created` é sufixo reservado do OpenMetrics e é removido no scrape — as queries do Grafana usam `orders_total`.

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
- **Messaging**: AWS SDK for Kotlin (SNS + SQS)
- **Observability**: Micrometer Prometheus, logstash-logback-encoder
- **Testing**: JUnit 5, MockK, TestContainers, Cucumber (BDD)
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
./gradlew bddTest                 # Fluxo BDD (Cucumber) ponta-a-ponta; requer Docker
./gradlew jacocoAggregatedReport  # Relatorio em build/reports/jacoco/...
```

O `bddTest` cobre o fluxo completo (criação → OrderCreated → pagamento → execução → conclusão) e um cenário de compensação (`PartsUnavailable` → `CANCELED`), com billing/execution simulados por eventos na fila do LocalStack (`main/src/test/resources/features/order_flow.feature`).

### Cobertura

| Métrica | Valor |
|---|---|
| Cobertura (SonarCloud) | **80.9%** |
| Testes | 91 |
| Quality gate | Passed |

Análise a cada PR pelo step `Sonar` do `pr-check.yaml`, no projeto `auto-repair-shop`
da organização `ivanzao` no SonarCloud. O quality gate exige 80% de cobertura em
código novo.

Ficam fora da contagem de cobertura o wiring de framework (`config`, `auth`,
`metric`), o módulo `main` e os DTOs — código sem lógica de negócio própria. Eles
seguem analisados para bugs, code smells e security hotspots.

Para reproduzir localmente:

```bash
./gradlew test integrationTest bddTest jacocoAggregatedReport
# relatório HTML em build/reports/jacoco/jacocoAggregatedReport/html/index.html
```

<!-- TODO: print do dashboard do SonarCloud (projeto é privado, link exige login) -->


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
│   └── kustomization.yaml
└── overlays/
    ├── hml/{kustomization,deployment-patch,configmap-patch}.yaml
    └── prod/{kustomization,deployment-patch,configmap-patch}.yaml
```

### Aplicar manualmente

```bash
kubectl kustomize infra/k8s/overlays/hml | kubectl apply -f -
```

### Valores dinâmicos por env

O CI lê os params do SSM, busca credenciais do DB no Secrets Manager (JSON com host/port/dbname/username/password) e patcheia o ConfigMap antes do apply:

| Param SSM | Conteúdo | Uso |
|-----------|----------|-----|
| `/auto-repair-shop/{env}/eks/cluster-name` | nome do cluster EKS | `aws eks update-kubeconfig` |
| `/auto-repair-shop/{env}/db/secret-arn` | ARN do Secrets Manager com credenciais do app | `aws secretsmanager get-secret-value` → JSON com host, port, dbname, username, password |
| `/auto-repair-shop/{env}/sns/order-events-topic-arn` | ARN do tópico de eventos do order | `SNS_TOPIC_ARN` no ConfigMap |
| `/auto-repair-shop/{env}/sqs/order-saga-queue-url` | URL da fila de entrada do order | `SQS_QUEUE_URL` no ConfigMap |
| `/auto-repair-shop/{env}/apigw/endpoint` | endpoint do API Gateway | smoke test pós-deploy |

Todos os params são **obrigatórios** — se algum não existir, o deploy falha no step de leitura SSM. Cluster e DB são publicados em `hml/ssm.tf`/`prod/ssm.tf`; tópico e fila do order vêm do `modules/messaging` (Plano 1 do `auto-repair-shop-infra`); APIGW vem do módulo de gateway.

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
