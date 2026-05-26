# ADR-003 — Infraestrutura monorepo (K8s + DB no mesmo repo)

**Status:** Accepted
**Data:** 2026-05-25

## Contexto

O enunciado do Tech Challenge 3 sugere a estrutura:

> Organizar o projeto em **quatro repositórios separados**:
> 1. Lambda (Function Serverless)
> 2. Infraestrutura Kubernetes (Terraform)
> 3. Infraestrutura do Banco de Dados Gerenciado (Terraform)
> 4. Aplicação principal executando em Kubernetes

Nossa entrega tem **3 repositórios**:

| Exigido | Entregue |
|---|---|
| Lambda | ✅ `auto-repair-shop-lambdas` |
| K8s Infra (TF) | ⚠️ consolidado em `auto-repair-shop-infra` |
| DB Infra (TF) | ⚠️ consolidado em `auto-repair-shop-infra` |
| App | ✅ `auto-repair-shop` |

Este ADR justifica formalmente a consolidação.

## Decisão

**Manter K8s e DB no mesmo repositório (`auto-repair-shop-infra`)**, organizados em modules separados:
- `modules/vpc/`
- `modules/eks/`
- `modules/rds/`
- `modules/db/`
- `modules/k8s/` (Helm releases, namespaces, db-init job, observability)
- `modules/gateway/`
- `modules/messaging/`
- `modules/registry/`

State separado por ambiente (`hml/terraform.tfstate`, `prod/terraform.tfstate`) no S3.

## Alternativas consideradas

### A. Separar em 2 repos (`-infra-k8s` + `-infra-db`)

**Prós**: literal do enunciado.

**Contras**:
- **Blast radius já está mitigado por env** (hml/prod separados), não por camada
- **AWS Academy só tem `LabRole`** — não dá pra criar roles IAM dedicadas por sub-projeto. A separação não traz isolamento real de credenciais
- **Ordem de bootstrap fica frágil**: o DB precisa do SG da VPC (criada no repo K8s), e o app precisa do endpoint do DB. Cross-repo coupling exigiria publicar/consumir SSM entre dois pipelines Terraform separados
- **Drift maior**: duplicação de provider config, lock files, backend.tf, secrets pipeline

### B. Monorepo + sub-projetos por camada (com states distintos)

**Prós**: state isolation por camada (rede, db, k8s).

**Contras**:
- Multiplica significativamente o overhead operacional (4 states por env × 2 envs = 8 states)
- Lab AWS Academy expira sessões frequentemente — gerenciar inicialização de 8 states extras quebra mais que ajuda
- Para o tamanho desse projeto, é over-engineering

### C. Consolidar tudo num único repo "fullstack"

Inviável: a app é maturada por developers e tem PR cadence própria. Lambdas têm pipeline e linguagem diferentes. Misturar com TF/Helm cria PRs gigantes e ruído.

## Consequências

**Positivas**:
- Bootstrap de um ambiente novo é 1 comando (`terraform apply` em `hml/`)
- Mudanças cross-camada (e.g., adicionar SG novo no RDS e abrir no SG do EKS) viram 1 PR
- Pipeline único, secrets únicos, lock file único
- Two-pass apply orquestra Helm + manifests dependentes de CRDs (vide `deploy.yaml`)

**Negativas**:
- **Desvio do enunciado** — risco de perda parcial de nota
- Sem isolamento de blast radius entre K8s e DB; um apply mal feito pode tocar ambos
- State maior, plan mais lento (~30s em vez de ~10s/camada)

**Mitigações**:
- Modules internos (`modules/rds/`, `modules/k8s/`) têm o **mesmo papel lógico** de repos separados — refactor pra splitar pode ser feito quando o lab for substituído por conta IAM real
- Pipeline tem proteção: PR check com `terraform plan` por env, deploy só em `push main` sequencial hml→prod
- ADR documentado com justificativa clara para o avaliador

## Referências

- [auto-repair-shop-infra README](https://github.com/ivanzao/auto-repair-shop-infra)
- [auto-repair-shop-infra ADR-001 — AWS Academy storage constraints](https://github.com/ivanzao/auto-repair-shop-infra/blob/main/docs/adrs/ADR-001-aws-academy-storage-constraints.md)
- [Plan: tech-challenge-cloud-platform-design](../../superpowers/specs/2026-05-13-tech-challenge-cloud-platform-design.md)
