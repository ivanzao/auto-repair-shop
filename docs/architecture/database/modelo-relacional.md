# Modelo Relacional — Justificativa formal

## 1. Justificativa da escolha do banco

Vide [RFC-002 — PostgreSQL gerenciado](../rfcs/RFC-002-database-postgresql.md). Resumo:

PostgreSQL 16 via RDS foi escolhido por 4 razões críticas:

1. **Transactional outbox**: gravar mudança de estado + evento na mesma transação é fundamental para consistência eventual com o consumidor SQS/Lambda email. Postgres ACID garante atomicidade.
2. **Tipos ricos**: UUID nativo (PK), JSONB (payload de eventos), CHECK constraints (enums simulados), índices parciais.
3. **Ecossistema Kotlin**: Exposed ORM tem o melhor suporte a Postgres. Flyway migra com confiabilidade.
4. **RDS gerencia**: backup automático, encryption-at-rest, parameter groups, snapshots manuais antes de deploy.

DynamoDB foi rejeitado pelo perfil relacional (joins frequentes: cliente → veículos → ordens). MySQL e SQL Server foram rejeitados por menor ROI vs Postgres no nosso stack.

## 2. Visão geral do modelo

Veja o diagrama completo em [04 — ER Model](../diagrams/04-er-model.md). Entidades principais:

- **Comerciais**: `customers`, `vehicles`, `services`, `supplies`
- **Operacionais**: `orders`, `order_services`, `order_supplies`, `service_supplies`, `order_schedules`, `order_execution_metrics`, `order_approval_tokens`
- **Mensageria/Outbox**: `events`, `processed_events`, `commands`, `shedlock`

> `users` e `refresh_tokens` pertencem ao **Lambda login** (`auto-repair-shop-lambdas`) — gerenciados por migration própria e não fazem parte deste schema.

## 3. Decisões de schema

| Decisão | Justificativa |
|---|---|
| **UUID como PK em todas as tabelas de domínio** | Desacopla geração da chave da inserção (cliente pode gerar antes do POST). Necessário no outbox: o ID do evento é referenciado antes do commit. |
| **`version INT` em entidades versionáveis** (`orders`, `attendants`, `customers`) | Optimistic locking via Exposed — protege contra updates concorrentes em status transitions sem usar `SELECT ... FOR UPDATE`. |
| **`document` UNIQUE em `attendants`, `customers`** | CPF/CNPJ é identidade externa. UNIQUE física no banco impede duplicatas mesmo em race conditions. |
| **`status VARCHAR` com `CHECK` em vez de `ENUM` SQL** | `ALTER TYPE ADD VALUE` no Postgres é OK em 12+, mas não dá pra remover valor. VARCHAR + CHECK migra mais facilmente. |
| **`events.external BOOLEAN`** | Marca eventos que devem ser relay-ed pro SNS. Permite evento "interno" (in-process via EventBus) sem reescrever o subsistema. |
| **`processed_events (event_id, consumer_id)` PK composta** | Idempotência multi-consumer: o mesmo evento pode ser processado por handlers diferentes; cada par é único. |
| **`order_approval_tokens.id UUID PK`** | Token UUID single-use enviado no email para o cliente. Sem PII, válido por TTL configurado, marcado `used=true` na primeira utilização. |
| **`shedlock` table** | Locking distribuído pro scheduler do app — múltiplos pods da app não disputam o outbox simultaneamente. |
| **`attendants` separado de `users` (Lambda)** | Migration V13 separou domínio de credenciais: `attendants` é entidade de domínio (CRUD via app), `users` (Lambda) guarda credenciais. Schema do Lambda é gerenciado de forma independente. |

## 4. Índices

Migrations criam índices estratégicos:

| Índice | Propósito |
|---|---|
| `idx_orders_status` (criado em V9) | Dashboard de operação filtra por status |
| `idx_orders_customer_id` | Listagem "minhas OS" |
| `idx_events_unprocessed` (partial: `WHERE processed=false`) | Outbox relay scan eficiente |
| `idx_processed_events_event_id` | Verificar idempotência em O(log n) |

## 5. Relacionamentos cardinais

- **CUSTOMER 1 — N VEHICLE**: um cliente possui múltiplos veículos
- **CUSTOMER 1 — N ORDER**: cliente abre múltiplas ordens ao longo do tempo
- **VEHICLE 1 — N ORDER**: histórico de manutenção rastreável por veículo
- **ATTENDANT 1 — N ORDER**: cada OS é atendida por exatamente 1 atendente, mas atendente atende várias
- **ORDER N — N SERVICE** (via `order_services`)
- **ORDER N — N SUPPLY** (via `order_supplies`, com quantidade consumida)
- **SERVICE N — N SUPPLY** (via `service_supplies`, default de insumos por serviço)
- **ORDER 1 — N APPROVAL_TOKEN**: cada envio de email gera token novo
- **ORDER 1 — 1 EXECUTION_METRIC**: registra `in_progress_at` e `completed_at` (UNIQUE force singleton)

## 6. Histórico de migrations (Flyway)

| Versão | Conteúdo |
|---|---|
| V1 | `users` (legado), `supplies`, `customers`, `vehicles` |
| V2 | `services`, `service_supplies` |
| V3 | `orders`, `order_services`, `order_supplies` |
| V4 | `commands` (outbox de comandos) |
| V5 | `events` (outbox de eventos) |
| V6 | `processed_events` (idempotência) |
| V7 | `refresh_tokens` (dropado em V13 via CASCADE) |
| V8 | `order_schedules` (entrega/retorno do veículo) |
| V9 | Índices para outbox e queries de status |
| V10 | `shedlock` (lock distribuído) |
| V11 | `order_approval_tokens` (tokens single-use para cliente) |
| V12 | `order_execution_metrics` (tempo em execução) |
| V13 | Dropa `users` legado + `refresh_tokens` (CASCADE); cria `attendants` (entidade de domínio sem credenciais) |

## 7. Bancos por ambiente

Cada cluster RDS hospeda 2 databases lógicos:

| DB | Propósito | Owner |
|---|---|---|
| `auto_repair_shop_hml` / `auto_repair_shop_prod` | App principal + Lambda login (tabela `users` própria do Lambda) | `app_hml` / `app_prod` |
| `grafana_hml` / `grafana_prod` | Backend de metadados do Grafana | `grafana_hml` / `grafana_prod` |

Roles e databases são criados por um **Kubernetes Job** in-cluster (`modules/k8s/db-init.tf`) — não via Terraform-provider-postgresql, que exigiria expor o RDS pro runner.

Credenciais vivem em Secrets Manager (1 secret JSON por env contendo `host`, `port`, `dbname`, `username`, `password`).
