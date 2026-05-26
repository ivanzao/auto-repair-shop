# ADR-002 — Lambda Authorizer com header injection (app não revalida JWT)

**Status:** Accepted
**Data:** 2026-05-25

## Contexto

O JWT é assinado pelo `Lambda login` (HS256) com segredo `JWT_HMAC`. Toda request para `/v1/{proxy+}` passa pelo `Lambda authorizer` no API Gateway, que valida o token.

Duas estratégias para o app downstream usar a identidade do usuário:

1. **App revalida JWT**: encaminha-se o header `Authorization: Bearer <jwt>` adiante, e o app valida assinatura novamente.
2. **App confia em headers injetados pelo gateway**: authorizer retorna `context: { userId, role, cpf }`, API Gateway injeta como `X-User-Id` / `X-User-Role`, e o app só lê.

## Decisão

**Adotar opção 2** — o app não revalida o JWT, lê `X-User-Id` e `X-User-Role` diretamente.

Wiring no `auto-repair-shop-infra/modules/gateway/routes.tf`:

```hcl
request_parameters = {
  "overwrite:header.X-User-Id"   = "$context.authorizer.userId"
  "overwrite:header.X-User-Role" = "$context.authorizer.role"
}
```

`overwrite:` (não `append:`) impede que cliente externo envie esses headers diretamente — API Gateway sobrescreve.

No Ktor (`api/src/main/kotlin/.../auth/JwtBearerAuthenticationProvider.kt`), o provider lê dos headers e popula o `Principal`.

## Alternativas consideradas

- **App revalida JWT** (opção 1): rejeitada porque (a) duplica a lógica de validação, (b) exige propagar `JWT_HMAC` para os pods (acrescenta superfície de ataque e gerenciamento de segredo), (c) authorizer já cacheia resultado por 5min — revalidar no app é overhead desperdiçado.
- **Authorizer assimétrico (RSA)**: poderia evitar compartilhar segredo entre dois pontos, mas Lambda+API GW HMAC é mais simples e custo de geração de chave inviável no AWS Academy.
- **API Gateway JWT authorizer nativo** (sem Lambda custom): mais simples, mas exige issuer JWKS público — não temos KMS pra hospedar chave pública. Lambda custom dá controle.

## Consequências

**Positivas**:
- App não precisa do `JWT_HMAC` — reduz superfície de ataque
- Pods stateless: qualquer pod processa qualquer request sem coordenação
- Cache de authorizer (TTL 5min) reduz invocações de Lambda

**Negativas**:
- Mudar formato de claims requer redeploy de ambos lambda e infra (request_parameters)
- Header `X-User-Role` é confiável **só** se a integração for `app` (com authorizer). Para a integração `app_public` (rotas `/v1/orders/quote/*` sem authorizer), os headers chegam vazios — o app deve tratar.

**Mitigações**:
- App valida que `X-User-Id` está presente nos endpoints autenticados via Ktor `authenticate{}` plugin
- Rotas públicas (quote approve/decline) não dependem dos headers — usam token UUID single-use no DB como credencial

## Referências

- [02 — Sequence: Autenticação](../diagrams/02-sequence-auth.md)
- [RFC-003 — Auth CPF + senha](../rfcs/RFC-003-auth-cpf-password.md)
- Gateway routes: [`auto-repair-shop-infra/modules/gateway/routes.tf`](https://github.com/ivanzao/auto-repair-shop-infra/blob/main/modules/gateway/routes.tf)
