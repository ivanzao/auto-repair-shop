# 02 — Sequence: Autenticação CPF + JWT

Fluxo completo do login até o consumo de uma rota protegida.

```mermaid
sequenceDiagram
    participant U as Atendente (browser)
    participant GW as API Gateway HTTP API
    participant LL as Lambda login
    participant LA as Lambda authorizer
    participant SM as Secrets Manager
    participant DB as RDS PostgreSQL
    participant App as App (EKS)

    Note over U,App: 1) Login
    U->>GW: POST /auth/login { cpf, password }
    GW->>LL: invoke (sem authorizer)
    LL->>LL: valida CPF (dígito verificador)
    LL->>SM: GetSecretValue(db creds)
    SM-->>LL: { host, port, user, password }
    LL->>DB: SELECT users WHERE document=$1 AND status='ACTIVE'
    DB-->>LL: { id, role, hashed_password }
    LL->>LL: bcrypt.Verify(input, hashed_password)
    LL->>LL: jwt.Sign(claims, JWT_HMAC, 1h)
    LL-->>GW: 200 { token }
    GW-->>U: 200 { token }

    Note over U,App: 2) Request autenticado
    U->>GW: GET /v1/orders<br/>Authorization: Bearer <jwt>
    GW->>LA: invoke authorizer (REQUEST type)
    LA->>LA: jwt.Verify(token, JWT_HMAC)
    LA-->>GW: { isAuthorized: true, context: { userId, role, cpf } }
    GW->>GW: injetar headers X-User-Id, X-User-Role
    GW->>App: GET /v1/orders<br/>X-User-Id, X-User-Role (via VPC Link → NLB)
    App->>App: decoda headers (confia, não revalida JWT)
    App->>DB: SELECT orders ...
    DB-->>App: rows
    App-->>GW: 200 { orders }
    GW-->>U: 200 { orders }
```

## Garantias

- **JWT é HS256** — segredo `JWT_HMAC` por ambiente, vivendo em GitHub Secrets e injetado nas Lambdas via `update-function-configuration`. O app **não** conhece o segredo (não precisa revalidar).
- **TTL do authorizer**: 5 min de cache (`authorizer_result_ttl_in_seconds = 300`). Para revogação imediata é necessário rotacionar o `JWT_HMAC` (rotação de chave invalida todos os tokens).
- **CPF**: validação completa do dígito verificador antes de qualquer query ao banco — protege contra brute force com CPFs sintéticos.
- **Bcrypt**: comparação resistente a timing attacks (subtle).
- **Status do user**: query filtra `status='ACTIVE'`. Desativar = apenas mudar para `INACTIVE` (sem delete, preserva histórico).

## Erros mapeados

| Cenário | Resposta |
|---|---|
| CPF inválido (dígito errado) | 401 `invalid credentials` (mesma resposta de senha errada — não vaza existência) |
| User não encontrado ou `INACTIVE` | 401 `invalid credentials` |
| Senha errada | 401 `invalid credentials` |
| Token expirado ou inválido | Authorizer retorna `isAuthorized: false` → API GW responde 401 |
| Token válido mas role insuficiente | App responde 403 (validado pelo Ktor `authenticate("admin"|"attendant")`) |
