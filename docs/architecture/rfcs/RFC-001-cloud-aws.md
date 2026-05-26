# RFC-001 — Escolha de cloud: AWS

**Status:** Accepted
**Data:** 2026-05-25
**Autor:** Ivan

## Contexto

O Tech Challenge fase 3 exige uma plataforma corporativa com API Gateway, Function Serverless, banco gerenciado e Kubernetes. A escolha de cloud impacta: ferramentas IaC, ecossistema de observabilidade, custo, vendor lock-in e tempo de bootstrap.

Restrição adicional do ambiente acadêmico: **AWS Academy** disponível com `LabRole` único, créditos fixos, sem permissão pra criar IAM custom, OIDC providers ou roles próprias.

## Opções consideradas

### Opção A — AWS (escolhida)

**Prós**:
- Disponível com créditos via AWS Academy (custo zero pro projeto)
- API Gateway HTTP API v2 maduro, com suporte nativo a Lambda Authorizer e VPC Link v2 (NLB integração)
- EKS gerenciado, RDS PostgreSQL gerenciado, Lambda com container image
- Ecossistema Terraform robusto (`hashicorp/aws`)
- Documentação extensa do agente Java do OpenTelemetry pra EKS

**Contras**:
- Vendor lock-in em alguns serviços (API Gateway, Lambda) — mitigado por adotar 12-factor app e domínio limpo
- AWS Academy limita criação de IAM roles (somente `LabRole`) — afeta IRSA, S3 IMDS, etc

### Opção B — GCP (GKE + Cloud Run + Cloud SQL)

**Prós**: Cloud Run mais simples que Lambda + ECR pull-through.
**Contras**: Sem créditos acadêmicos garantidos no programa, time tem menos familiaridade com Terraform GCP.
**Rejeitado**: sem acesso garantido a créditos.

### Opção C — Azure (AKS + Functions + Azure Database)

**Prós**: Azure for Students disponível.
**Contras**: API Management mais caro que API Gateway HTTP, integração com observabilidade open-source (LGTM stack) menos polida que na AWS.
**Rejeitado**: documentação Terraform menos rica pra a stack escolhida.

## Recomendação

**Adotar AWS**.

A combinação API Gateway HTTP + Lambda + EKS + RDS é nativa e bem documentada. O AWS Academy resolve o problema de custo. As limitações de IAM são contornáveis (ver [ADR-002 — Lambda Authorizer header injection](../adrs/ADR-002-lambda-authorizer-header-injection.md) e ADRs específicos do repo `auto-repair-shop-infra`).

## Consequências

**Positivas**:
- Custo zero durante o projeto
- Stack alinhada com a maioria das vagas de mercado no Brasil
- Terraform `hashicorp/aws` com cobertura completa

**Negativas**:
- Lock-in em API Gateway HTTP (formato de payload v2 específico)
- Lambdas usam runtime AWS-específico (`provided.al2023-arm64`)

**Mitigações**:
- Domínio (`auto-repair-shop`) está em Kubernetes — portável pra qualquer cloud
- Lambdas em Go puro: trocar runtime exige reescrever só o `main.go` (handler signature)

## Referências

- [Plan: tech-challenge-cloud-platform-design](../../superpowers/specs/2026-05-13-tech-challenge-cloud-platform-design.md)
- [auto-repair-shop-infra README](https://github.com/ivanzao/auto-repair-shop-infra)
