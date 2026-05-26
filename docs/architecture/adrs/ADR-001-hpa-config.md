# ADR-001 — HPA: 70% CPU, 2–4 réplicas

**Status:** Accepted
**Data:** 2026-05-25

## Contexto

A app Kotlin/Ktor roda em EKS num namespace por ambiente (`auto-repair-shop-hml`/`-prod`). O enunciado exige cluster com escalabilidade.

A workload é heterogênea:
- API REST síncrona (latência sensível)
- Scheduler interno (`EventProcessorTask` + `CommandProcessorTask` a cada 5s)
- Consumidores SQS/EventBus

Profile observado em load test (K6, 50 VUs por 10min):
- CPU médio por pod: ~30%
- Picos a 60–75% durante criação de OS

## Decisão

Configurar **HorizontalPodAutoscaler** com:
- `minReplicas: 2` (HA mínima — 2 AZs)
- `maxReplicas: 4` em prod / `2` em hml (limite do node group AWS Academy)
- `targetCPUUtilizationPercentage: 70`
- Cooldown padrão (5min scale-up, 5min scale-down)

Resource requests/limits do container:
- CPU request: 250m, limit: livre (best-effort soft)
- Memory request: 512Mi, limit: 1Gi

`hpa.yaml` no `infra/k8s/base/` (Kustomize base) com overlay por env ajustando `maxReplicas`.

## Alternativas consideradas

- **HPA por métrica custom** (`orders_created_total` rate): rejeitado por complexidade — exige adapter Prometheus → metric-server. CPU como proxy é suficiente.
- **VPA (Vertical Pod Autoscaler)**: rejeitado — incompatível com HPA simultâneo em modo Auto. Workload tem variação de uso muito alta pra justificar tuning vertical.
- **KEDA com SQS depth**: interessante pros consumers SQS, mas o consumer roda na própria app — separar workers num Deployment dedicado seria refactor grande, fora de escopo.
- **Sem HPA, replicas fixas**: rejeitado — não atende o requisito "cluster escalável" do enunciado.

## Consequências

**Positivas**:
- Resposta natural a picos de tráfego (load test mostra scale-up em ~90s)
- 2 réplicas mínimas garantem rolling deploy sem downtime
- CPU 70% deixa headroom pra spike antes do scale-up completar

**Negativas**:
- Cooldown de 5min pode subprovisionar em picos curtos (~3min)
- AWS Academy tem nodegroup pequeno (2 nodes de `t3.medium`) — em hml, `maxReplicas=2` é o teto físico do cluster

**Mitigações**:
- Limite vertical (`requests/limits`) impede pods saturarem o nodegroup
- Tracing/APM dashboard ([APM dashboard](../../infra/k8s/manifests/) no Grafana) mostra latência p95/p99 e ajuda detectar quando HPA é insuficiente
