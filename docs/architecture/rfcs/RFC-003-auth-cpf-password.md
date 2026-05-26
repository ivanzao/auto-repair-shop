# RFC-003 — Autenticação via CPF + senha + JWT

**Status:** Accepted
**Data:** 2026-05-25
**Autor:** Ivan

## Contexto

O enunciado do Tech Challenge 3 exige:

> Proteger rotas sensíveis da aplicação com autenticação via CPF.
> Criar uma Function Serverless para validar o CPF do cliente, consultar a existência e o status do cliente, gerar e devolver um token JWT.

A leitura literal sugere CPF como **único** fator. Esse modelo é vulnerável (CPF é semi-público no Brasil). Decisão de produto: usar CPF + senha como par de credenciais, mantendo JWT como bearer token consumido pelo API Gateway.

Os atores do sistema são **atendentes e administradores** da oficina — não o cliente final. O cliente recebe links por email (com token UUID single-use no DB) para aprovar/recusar orçamentos.

## Opções consideradas

### Opção A — CPF + senha (escolhida)

**Prós**:
- Cumpre o requisito literal de "validar CPF"
- Adiciona fator secreto (senha bcrypt) — protege contra ataque com CPFs sintéticos
- Lambda separada faz a verificação; app não conhece o segredo JWT
- Compatível com fluxo padrão de login web

**Contras**:
- Requer interface de provisionamento de senha (admin cria atendente + define senha inicial)
- Recovery de senha exige email/SMS (fora de escopo desta entrega)

### Opção B — Só CPF (interpretação literal do enunciado)

**Contras**: vulnerável. CPF vaza em data leaks, redes sociais e órgãos públicos. Sem segundo fator, qualquer leak permite acesso.
**Rejeitado**: insegurança inaceitável para sistema corporativo.

### Opção C — CPF + senha via Cognito User Pool

**Prós**: Cognito gerencia password policy, recovery, MFA opcional.
**Contras**: Overkill pro escopo. Adiciona dependência de Cognito, custo, e complexidade na integração com Lambda Authorizer (que validaria token Cognito).
**Rejeitado**: complexidade desproporcional ao ganho.

### Opção D — Passwordless (magic link, OTP por SMS)

**Prós**: Sem senha pra gerenciar.
**Contras**: Operacional ruim pra atendente em loja — pedir que abra email a cada login é fricção. Cliente final SIM usa magic link (vide aprovação de orçamento).
**Rejeitado** pro fluxo de atendente.

## Recomendação

**CPF + senha + JWT HS256**.

Arquitetura:
- Lambda `login`: valida CPF, lê `users` em `RDS`, verifica `bcrypt`, emite JWT (HMAC-SHA256, TTL 1h)
- Lambda `authorizer`: valida assinatura JWT (mesmo segredo via env var), injeta `X-User-Id` e `X-User-Role`
- App: confia nos headers injetados (não revalida JWT, vide [ADR-002](../adrs/ADR-002-lambda-authorizer-header-injection.md))
- Segredo `JWT_HMAC` por ambiente: vive em GitHub Secrets, é injetado no `update-function-configuration` pelo CI

## Consequências

**Positivas**:
- Lambdas pequenas (~500 LoC), fáceis de auditar
- Performance: authorizer responde em <100ms (cold start ~300ms)
- TTL de 5 min no cache de authorizer (API GW) reduz invocações

**Negativas**:
- TTL de cache atrasa revogação em até 5min (mitigação: rotacionar `JWT_HMAC`)
- Bcrypt cost factor 10 = ~100ms por login (aceitável)

**Mitigações**:
- Rotação periódica do `JWT_HMAC` via GH Action manual
- `users.status = 'INACTIVE'` desativa imediatamente (mas só após cache TTL expirar)

## Referências

- [02 — Sequence: Autenticação](../diagrams/02-sequence-auth.md)
- [ADR-002 — Lambda Authorizer header injection](../adrs/ADR-002-lambda-authorizer-header-injection.md)
- Lambda code: [`auto-repair-shop-lambdas/cmd/login/main.go`](https://github.com/ivanzao/auto-repair-shop-lambdas/blob/main/cmd/login/main.go)
