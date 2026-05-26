# RFC-002 — Banco: PostgreSQL gerenciado (RDS)

**Status:** Accepted
**Data:** 2026-05-25
**Autor:** Ivan

## Contexto

O domínio do Auto Repair Shop é **fortemente relacional**: cliente possui veículos, veículo tem múltiplas ordens de serviço, cada OS tem vários serviços e suprimentos consumidos, atendente atende várias OS. Existem ainda tabelas operacionais (outbox de eventos, idempotência, locks distribuídos via ShedLock, refresh tokens).

O enunciado exige justificativa formal da escolha do banco.

## Opções consideradas

### Opção A — PostgreSQL gerenciado via RDS (escolhida)

**Prós**:
- ACID transactional necessário pro outbox pattern (gravar mudança de estado + evento na mesma transação, vide [03 — Sequence: Create Order](../diagrams/03-sequence-create-order.md))
- Suporta UUID nativo, JSONB (payload de eventos), CHECK constraints (enums simulados), índices parciais (outbox)
- Ecossistema rico no JVM: Exposed ORM, Flyway migrations, HikariCP pool, drivers Postgres maduros
- RDS gerencia backup, encryption-at-rest, multi-AZ failover, parameter groups
- Compatível com Grafana (datasource oficial pra dashboards de negócio que consultam OS por status)

**Contras**:
- Custo de instância 24/7 (mas mitigado por créditos AWS Academy)
- Connection pooling em lambda exige cuidado (resolvido reabrindo o pool no `init()` do handler)

### Opção B — MySQL/MariaDB gerenciado

**Prós**: Similar a Postgres em maturidade.
**Contras**: JSONB do Postgres é mais performático que JSON do MySQL. Foreign keys + DDL no MySQL têm corner cases (sem `CREATE INDEX IF NOT EXISTS`). Ecossistema Kotlin/Exposed pende mais pro Postgres.
**Rejeitado**: nenhuma vantagem técnica clara.

### Opção C — DynamoDB

**Prós**: Serverless, scaling automático, pay-per-request, integra naturalmente com Lambda.
**Contras**: Modelo NoSQL não suporta joins. O domínio precisa de queries do tipo "todas as OS deste cliente neste período" e "histórico de serviços do veículo X" — ineficientes em DynamoDB sem desnormalização agressiva e GSI múltiplos. Sem ACID multi-item antes de PartiQL (e ainda limitado).
**Rejeitado**: incompatível com o domínio relacional.

### Opção D — SQL Server (RDS)

**Prós**: Maturidade enterprise.
**Contras**: Custo de licença, ecossistema Linux/Kotlin menos otimizado.
**Rejeitado**: custo proibitivo no contexto acadêmico e produção.

### Opção E — Aurora Serverless v2 (PostgreSQL-compatible)

**Prós**: Auto-scaling, paga só pelo uso.
**Contras**: Cold start de até 30s, custo mínimo de 0.5 ACU mesmo idle. Sem benefício real pra workload baixo desse projeto.
**Rejeitado**: complexidade extra sem ROI.

## Recomendação

**Adotar PostgreSQL 16 via RDS** (`db.t3.micro` no AWS Academy).

Configuração:
- 1 instância por ambiente (`hml`, `prod`) — VPCs separadas
- Multi-AZ desabilitado em hml (custo), habilitado em prod
- Backup automático 7 dias
- Encryption-at-rest com KMS default

Schema:
- Migrations Flyway no app (`storage/src/main/resources/db/migration/V*.sql`)
- Migration Lambda separada pra `users` (no repo `auto-repair-shop-lambdas`)
- Job in-cluster cria roles e databases por env (em vez de IAM auth, bloqueado no Academy)

## Consequências

**Positivas**:
- Transactional outbox funciona out-of-the-box (mesma transação grava `orders` + `events`)
- Flyway versiona schema com confiança
- Grafana consulta Postgres direto pra dashboards customizados (futuro)

**Negativas**:
- Lambda login precisa reabrir conexão a cada cold start (mitigado com `init()` global)
- Custo de instância 24/7

**Mitigações**:
- Connection pool no app via HikariCP (10 conexões por pod, HPA limita pods a 4 → 40 max)
- Lambda usa `sslmode=require` mas mantém pool de 1 conexão por container

## Referências

- [Modelo Relacional](../database/modelo-relacional.md) — justificativa de schema, índices e relacionamentos
- [04 — ER Model](../diagrams/04-er-model.md)
- Migrations: [V1–V13](../../../storage/src/main/resources/db/migration/)
