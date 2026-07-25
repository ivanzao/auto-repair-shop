# 03 — Sequence: Abertura de Ordem de Serviço

Fluxo desde o POST de criação até a aprovação do orçamento pelo cliente via email.

```mermaid
sequenceDiagram
    participant U as Atendente
    participant GW as API Gateway
    participant LA as Lambda authorizer
    participant App as App (EKS)
    participant DB as RDS PostgreSQL
    participant Sched as Event scheduler
    participant SNS as SNS topic
    participant SQS as SQS queue
    participant LE as Lambda email
    participant MS as MailerSend
    participant C as Cliente (email)

    Note over U,DB: 1) Criar OS
    U->>GW: POST /v1/orders<br/>{ customerId, vehicleId, services, ... }
    GW->>LA: authorizer (cache 5min)
    LA-->>GW: isAuthorized=true
    GW->>App: POST /v1/orders + X-User-Id, X-User-Role
    App->>DB: TX BEGIN
    App->>DB: INSERT INTO orders (status='RECEIVED', ...)
    App->>App: ordersCreated.increment()<br/>orders_by_status_total{status="RECEIVED"}.increment()
    App->>App: logger.info("Order created orderId=... status=RECEIVED")
    App-->>GW: 201 { order }
    GW-->>U: 201 { order }

    Note over U,MS: 2) Finalizar diagnóstico → envia email
    U->>App: POST /v1/orders/{id}/diagnosis/finish (via GW)
    App->>DB: TX BEGIN
    App->>DB: UPDATE orders SET status='WAITING_APPROVAL'
    App->>DB: INSERT INTO events (OrderDiagnoseFinishedEvent, external=true)
    App->>DB: TX COMMIT
    App->>App: orders_by_status_total{status="WAITING_APPROVAL"}.increment()
    App-->>GW: 200 { order }

    loop EventProcessorTask (a cada 5s)
        Sched->>DB: SELECT events WHERE processed=false
        DB-->>Sched: [OrderDiagnoseFinishedEvent]
        Sched->>SNS: Publish(payload)
        SNS->>SQS: fan-out
        Sched->>DB: INSERT INTO processed_events
    end

    SQS->>LE: invoke (batch up to N)
    LE->>LE: renderiza template HTML<br/>+ link aprove/decline (token UUID)
    LE->>MS: POST /v1/email
    MS-->>LE: 202 accepted
    MS->>C: email com 2 links

    Note over C,App: 3) Cliente aprova
    C->>GW: GET /v1/orders/quote/approve?token=...<br/>(rota pública — sem authorizer)
    GW->>App: GET /v1/orders/quote/approve (via VPC Link, integração app_public)
    App->>DB: SELECT order_approval_tokens<br/>WHERE id=$token AND valid
    App->>DB: TX BEGIN
    App->>DB: UPDATE orders SET status='IN_PROGRESS'
    App->>DB: UPDATE token SET used=true
    App->>DB: INSERT INTO events (OrderInProgressEvent)
    App->>DB: TX COMMIT
    App->>App: orders_by_status_total{status="IN_PROGRESS"}.increment()
    App-->>C: 200 OK
```

## Pontos-chave

- **Transactional Outbox**: gravar mudança de estado + evento na mesma transação garante eventual consistency. Se o scheduler não rodar, ninguém solta SNS — mas o estado do banco está correto.
- **Idempotência**: `processed_events` evita duplicação. SQS DLQ recebe falhas após N tentativas.
- **ShedLock**: `EventProcessorTask` usa lock distribuído via tabela `shedlock` — múltiplos pods da app não disputam o outbox.
- **Aprovação por link público**: o cliente não tem JWT. A rota é exposta no API Gateway sem authorizer (integração `app_public`) e o token UUID single-use no DB serve de credencial.
- **Métricas observáveis** durante o fluxo:
  - `orders_total` (o meter se chama `orders_created_total`; `_created` é reservado e cai no scrape)
  - `orders_by_status_total{status}` em cada transição
  - `http_server_requests_seconds_*` (OTel auto-injection)
  - Logs em `OrderUseCase` com `orderId`, `status`, `traceId` (MDC)
