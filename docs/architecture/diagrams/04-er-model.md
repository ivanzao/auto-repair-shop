# 04 — ER Model

Modelo entidade-relacionamento do schema PostgreSQL reconciliado com as migrations Flyway (V1–V13).

```mermaid
erDiagram
    CUSTOMERS ||--o{ VEHICLES : "owns"
    CUSTOMERS ||--o{ ORDERS : "requests"
    VEHICLES  ||--o{ ORDERS : "subject_of"
    ATTENDANTS ||--o{ ORDERS : "attends"
    ORDERS ||--o{ ORDER_SERVICES : "includes"
    SERVICES ||--o{ ORDER_SERVICES : "applied_to"
    ORDERS ||--o{ ORDER_SUPPLIES : "consumes"
    SUPPLIES ||--o{ ORDER_SUPPLIES : "consumed_by"
    SERVICES ||--o{ SERVICE_SUPPLIES : "requires"
    SUPPLIES ||--o{ SERVICE_SUPPLIES : "required_by"
    ORDERS ||--o{ ORDER_APPROVAL_TOKENS : "has"
    ORDERS ||--|| ORDER_EXECUTION_METRICS : "measured_by"
    ORDERS ||--o{ ORDER_SCHEDULES : "scheduled_for"

    CUSTOMERS {
        UUID id PK
        VARCHAR document "UNIQUE - CPF/CNPJ"
        VARCHAR name
        VARCHAR email
        VARCHAR contact
        TIMESTAMP created_at
        TIMESTAMP modified_at
        INT version "optimistic lock"
    }

    VEHICLES {
        UUID id PK
        UUID client_id FK
        VARCHAR plate "UNIQUE"
        VARCHAR model
        VARCHAR brand
        INT year
        TIMESTAMP created_at
        TIMESTAMP modified_at
        INT version "optimistic lock"
    }

    ATTENDANTS {
        UUID id PK
        VARCHAR name
        VARCHAR document "UNIQUE - CPF"
        VARCHAR email "UNIQUE"
        VARCHAR contact
        TIMESTAMP created_at
        TIMESTAMP modified_at
        INT version "optimistic lock"
    }

    ORDERS {
        UUID id PK
        UUID customer_id FK
        UUID vehicle_id FK
        UUID attendant_id FK
        VARCHAR description
        VARCHAR status "RECEIVED | IN_DIAGNOSIS | WAITING_APPROVAL | IN_PROGRESS | COMPLETED | DELIVERED | CANCELED"
        TIMESTAMP created_at
        TIMESTAMP modified_at
        INT version "optimistic lock"
    }

    SERVICES {
        UUID id PK
        VARCHAR name
        NUMERIC price
        INT duration_minutes
    }

    SUPPLIES {
        UUID id PK
        VARCHAR name
        INT quantity
        NUMERIC price
    }

    ORDER_SERVICES {
        UUID order_id PK,FK
        UUID service_id PK,FK
    }

    ORDER_SUPPLIES {
        UUID order_id PK,FK
        UUID supply_id PK,FK
        INT quantity
    }

    SERVICE_SUPPLIES {
        UUID service_id PK,FK
        UUID supply_id PK,FK
        INT quantity
    }

    ORDER_APPROVAL_TOKENS {
        UUID id PK
        UUID order_id FK
        TIMESTAMP expires_at
        BOOLEAN used
    }

    ORDER_EXECUTION_METRICS {
        UUID id PK
        UUID order_id FK "UNIQUE"
        TIMESTAMP in_progress_at
        TIMESTAMP completed_at
    }

    ORDER_SCHEDULES {
        UUID id PK
        UUID order_id FK
        TIMESTAMP date_time
        VARCHAR type "DELIVERY | RETURN"
    }

    EVENTS {
        UUID id PK
        VARCHAR type
        JSONB payload
        BOOLEAN external "TRUE = relay para SNS"
        TIMESTAMP created_at
    }

    PROCESSED_EVENTS {
        UUID event_id PK,FK
        VARCHAR consumer_id PK
        TIMESTAMP processed_at
    }

    COMMANDS {
        UUID id PK
        VARCHAR type
        JSONB payload
        TIMESTAMP created_at
    }
```

## Relacionamentos principais

- **CUSTOMER 1—N VEHICLE**: cliente pode ter múltiplos veículos
- **CUSTOMER 1—N ORDER**: cliente abre múltiplas OS ao longo do tempo
- **VEHICLE 1—N ORDER**: histórico de manutenção por veículo
- **ATTENDANT 1—N ORDER**: cada OS é atendida por exatamente 1 atendente
- **ORDER N—N SERVICE** via `order_services`
- **ORDER N—N SUPPLY** via `order_supplies` (suprimentos consumidos com quantidade)
- **SERVICE N—N SUPPLY** via `service_supplies` (insumos default por tipo de serviço)
- **ORDER 1—N ORDER_APPROVAL_TOKEN**: cada vez que se envia email pro cliente aprovar, gera novo token UUID
- **ORDER 1—1 ORDER_EXECUTION_METRICS**: registro de tempo em execução, UNIQUE constraint garante singleton
- **EVENTS / PROCESSED_EVENTS**: outbox + idempotência

Ver [Modelo Relacional — Justificativa formal](../database/modelo-relacional.md) para discussão das escolhas de schema, índices e justificativa do banco.
