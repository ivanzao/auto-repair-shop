# Plan 4: app — Header auth, Outbox→SNS, Kustomize, Observability instrumentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adaptar o repo `auto-repair-shop` (existente) para a nova arquitetura: remover lógica de JWT (delegada ao API Gateway + Lambda Authorizer), adicionar header-based authentication, criar `SnsRelayEventHandler` que reaproveita o outbox existente para publicar em SNS, remover o módulo `email/` (Lambda assume), instrumentar com Micrometer + OTel, migrar K8s manifests pra Kustomize com overlays hml/prod, atualizar pipeline pra deploy via API Gateway com SSM.

**Architecture:** Mantém arquitetura hexagonal e estrutura multi-módulo. `jwt/` vira `HeaderAuthenticationProvider` que confia em `X-User-Id`/`X-User-Role` injetados pelo API Gateway. `worker/` ganha `SnsRelayEventHandler` que publica `DomainEvent`s marcados como externos em SNS — usando a tabela `events` existente como outbox e `EventProcessorTask` como relay. Kustomize substitui YAMLs duplicados; pipeline lê SSM pra config dinâmica.

**Tech Stack:** Kotlin 2.2, Ktor 3.3, Exposed, Flyway, Koin, Micrometer Prometheus, OpenTelemetry Ktor, AWS SDK Kotlin (SNS), Kustomize, Logback + logstash-logback-encoder.

---

## Pré-requisitos

- **Plans 1, 2, 3 completados** (cluster + DB + Lambdas/API GW prontos)
- Repo `auto-repair-shop` na máquina (já existe)
- Spec aprovada

---

## File Structure (mudanças)

```
auto-repair-shop/
├── api/src/main/kotlin/br/com/soat/api/
│   ├── auth/HeaderAuthenticationProvider.kt          (NOVO)
│   ├── ApiModule.kt                                  (modificado: remove rotas login)
├── domain/src/main/kotlin/br/com/soat/
│   ├── order/event/QuoteEmailRequestedEvent.kt       (NOVO)
│   └── event/EventPublisher.kt                       (modificado: flag external)
├── worker/src/main/kotlin/br/com/soat/
│   ├── messaging/SnsRelayEventHandler.kt             (NOVO)
│   ├── messaging/SnsClient.kt                        (NOVO)
│   └── publisher/DefaultEventPublisher.kt            (modificado)
├── main/src/main/kotlin/br/com/soat/
│   └── config/AppModule.kt                           (modificado: DI SNS, registro de external events)
├── storage/src/main/resources/db/migration/
│   └── V13__add_user_status.sql                      (NOVO)
├── jwt/                                              (módulo simplificado ou removido)
├── email/                                            (REMOVER módulo inteiro)
├── infra/k8s/                                        (RESTRUTURADO pra Kustomize)
│   ├── base/
│   │   ├── kustomization.yaml
│   │   ├── deployment.yaml
│   │   ├── service.yaml             # NLB privado
│   │   ├── configmap.yaml
│   │   ├── hpa.yaml
│   │   ├── servicemonitor.yaml
│   │   └── serviceaccount.yaml      # referencia IRSA criado em Plan 1
│   └── overlays/
│       ├── hml/{kustomization.yaml, deployment-patch.yaml, configmap-patch.yaml}
│       └── prod/{kustomization.yaml, deployment-patch.yaml, configmap-patch.yaml}
├── .github/workflows/
│   ├── pr-check.yaml                                 (atualizado)
│   └── deploy.yaml                                   (REESCRITO)
└── build.gradle.kts                                  (deps novas)
```

---

## Task 1: Migration `V13__add_user_status.sql`

**Files:**
- Create: `storage/src/main/resources/db/migration/V13__add_user_status.sql`

(V13 porque V12 já existe; verificar maior número atual e usar o próximo.)

- [ ] **Step 1: Confirmar próxima versão Flyway**

```bash
ls storage/src/main/resources/db/migration/ | sort
```

Expected: o último é `V12_*`. Usar `V13` (substituir se necessário).

- [ ] **Step 2: Criar migration**

```sql
-- V13__add_user_status.sql
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
```

- [ ] **Step 3: Atualizar `User.kt` domain model**

Em `domain/src/main/kotlin/br/com/soat/user/model/User.kt`, adicionar:

```kotlin
data class User(
    // ... campos existentes ...
    val status: UserStatus = UserStatus.ACTIVE,
) {
    enum class Role { ADMIN, ATTENDANT }
    enum class UserStatus { ACTIVE, INACTIVE }
}
```

E ajustar `storage/.../UserPostgresRepository.kt` pra ler/escrever `status`.

- [ ] **Step 4: Rodar testes locais**

```bash
./gradlew :domain:test :storage:test
```

- [ ] **Step 5: Commit**

```bash
git add storage/src/main/resources/db/migration/V13__add_user_status.sql \
        domain/src/main/kotlin/br/com/soat/user/model/User.kt \
        storage/src/main/kotlin/br/com/soat/user/UserPostgresRepository.kt
git commit -m "feat(user): add status column (ACTIVE/INACTIVE) via Flyway V13"
```

---

## Task 2: `HeaderAuthenticationProvider` no `api/`

**Files:**
- Create: `api/src/main/kotlin/br/com/soat/api/auth/HeaderAuthenticationProvider.kt`
- Modify: `api/src/main/kotlin/br/com/soat/api/ApiModule.kt` (ou onde Authentication é configurado)

- [ ] **Step 1: Criar teste**

```kotlin
// api/src/test/kotlin/br/com/soat/api/auth/HeaderAuthenticationProviderTest.kt
package br.com.soat.api.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderAuthenticationProviderTest {

    @Test
    fun `returns 401 when X-User-Id header absent`() = testApplication {
        application {
            install(Authentication) { headerAuth("test") {} }
            routing {
                authenticate("test") {
                    get("/me") { call.respond(HttpStatusCode.OK) }
                }
            }
        }
        val res = client.get("/me")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `populates Principal when X-User-Id and X-User-Role present`() = testApplication {
        application {
            install(Authentication) { headerAuth("test") {} }
            routing {
                authenticate("test") {
                    get("/me") {
                        val p = call.principal<UserPrincipal>()!!
                        call.respondText("${p.userId}:${p.role}")
                    }
                }
            }
        }
        val res = client.get("/me") {
            header("X-User-Id", "abc-123")
            header("X-User-Role", "ADMIN")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("abc-123:ADMIN", res.bodyAsText())
    }
}
```

- [ ] **Step 2: Rodar teste — espera fail**

```bash
./gradlew :api:test --tests HeaderAuthenticationProviderTest
```

- [ ] **Step 3: Implementar provider**

```kotlin
// api/src/main/kotlin/br/com/soat/api/auth/HeaderAuthenticationProvider.kt
package br.com.soat.api.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

data class UserPrincipal(val userId: String, val role: String) : Principal

class HeaderAuthenticationProvider(config: Config) :
    AuthenticationProvider(config) {

    class Config(name: String?) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val userId = call.request.headers["X-User-Id"]
        val role   = call.request.headers["X-User-Role"]
        if (userId.isNullOrBlank() || role.isNullOrBlank()) {
            context.challenge("HeaderAuth", AuthenticationFailedCause.NoCredentials) { ch, c ->
                c.respond(HttpStatusCode.Unauthorized)
                ch.complete()
            }
            return
        }
        context.principal(UserPrincipal(userId, role))
    }
}

fun AuthenticationConfig.headerAuth(
    name: String? = null,
    configure: HeaderAuthenticationProvider.Config.() -> Unit = {},
) {
    val provider = HeaderAuthenticationProvider(
        HeaderAuthenticationProvider.Config(name).apply(configure)
    )
    register(provider)
}
```

- [ ] **Step 4: Rodar testes — passa**

```bash
./gradlew :api:test
```

- [ ] **Step 5: Substituir uso do JWT provider em `ApiModule.kt`**

Encontrar onde `Authentication` é instalado (provavelmente `ApiModule.kt`), trocar `jwt(...)` por `headerAuth("api")`. Remover rotas `/auth/login` e `/auth/logout` se existirem (movem pra Lambda).

- [ ] **Step 6: Commit**

```bash
git add api/src/main/kotlin/br/com/soat/api/auth/ \
        api/src/test/kotlin/br/com/soat/api/auth/ \
        api/src/main/kotlin/br/com/soat/api/ApiModule.kt
git commit -m "feat(api): add HeaderAuthenticationProvider; remove JWT routes (delegated to Lambda)"
```

---

## Task 3: `QuoteEmailRequestedEvent`

**Files:**
- Create: `domain/src/main/kotlin/br/com/soat/order/event/QuoteEmailRequestedEvent.kt`

- [ ] **Step 1: Criar evento**

```kotlin
package br.com.soat.order.event

import br.com.soat.event.model.DomainEvent
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class QuoteEmailRequestedEvent(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val modifiedAt: LocalDateTime = LocalDateTime.now(),
    override val version: Int = 0,

    val orderId: UUID,
    val customerEmail: String,
    val customerName: String,
    val totalAmount: BigDecimal,
    val items: List<Item>,
) : DomainEvent {
    data class Item(val description: String, val unitPrice: BigDecimal, val quantity: Int)
}
```

- [ ] **Step 2: Commit**

```bash
git add domain/src/main/kotlin/br/com/soat/order/event/QuoteEmailRequestedEvent.kt
git commit -m "feat(event): add QuoteEmailRequestedEvent for SNS relay"
```

---

## Task 4: Flag `external` no `DomainEvent` + ajuste no `DefaultEventPublisher`

**Files:**
- Modify: `domain/src/main/kotlin/br/com/soat/event/model/DomainEvent.kt`
- Modify: `worker/src/main/kotlin/br/com/soat/publisher/DefaultEventPublisher.kt`

- [ ] **Step 1: Adicionar `external` ao DomainEvent**

```kotlin
package br.com.soat.event.model

import java.time.LocalDateTime
import java.util.UUID

interface DomainEvent {
    val id: UUID
    val createdAt: LocalDateTime
    val modifiedAt: LocalDateTime
    val version: Int
    val external: Boolean
        get() = false
}
```

- [ ] **Step 2: Marcar `QuoteEmailRequestedEvent` como external**

```kotlin
// adicionar dentro da data class:
override val external: Boolean = true,
```

(precisa estar na lista de propriedades porque é um override de val)

- [ ] **Step 3: Modificar `DefaultEventPublisher`**

```kotlin
package br.com.soat.publisher

import br.com.soat.bus.EventBus
import br.com.soat.event.EventPublisher
import br.com.soat.event.model.DomainEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class DefaultEventPublisher(
    private val dispatcher: CoroutineDispatcher,
    private val eventBus: EventBus
) : EventPublisher {

    private val logger = LoggerFactory.getLogger(DefaultEventPublisher::class.java)

    override fun publish(event: DomainEvent) {
        if (event.external) {
            // Não despacha in-memory — espera o scheduler ler o outbox após COMMIT
            logger.info("External event ${event::class.simpleName}/${event.id} queued for scheduler relay")
            return
        }
        try {
            logger.info("Publishing event ${event::class.simpleName} with id ${event.id}")
            CoroutineScope(dispatcher).launch { eventBus.publish(event) }
        } catch (e: Exception) {
            logger.warn("Failed to publish event ${event.id} immediately. Will be processed by scheduler.", e)
        }
    }
}
```

- [ ] **Step 4: Rodar tests existentes**

```bash
./gradlew :domain:test :worker:test
```

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/br/com/soat/event/model/DomainEvent.kt \
        domain/src/main/kotlin/br/com/soat/order/event/QuoteEmailRequestedEvent.kt \
        worker/src/main/kotlin/br/com/soat/publisher/DefaultEventPublisher.kt
git commit -m "feat(event): add external flag; DefaultEventPublisher skips in-memory for external events"
```

---

## Task 5: `SnsRelayEventHandler` + `SnsClient`

**Files:**
- Create: `worker/src/main/kotlin/br/com/soat/messaging/SnsClient.kt`
- Create: `worker/src/main/kotlin/br/com/soat/messaging/SnsRelayEventHandler.kt`

- [ ] **Step 1: Adicionar dep AWS SDK Kotlin no `worker/build.gradle.kts`**

```kotlin
dependencies {
    // ... existentes ...
    implementation("aws.sdk.kotlin:sns:1.2.0")
    implementation("aws.sdk.kotlin:secretsmanager:1.2.0")  // se ainda não tiver
}
```

- [ ] **Step 2: Criar `SnsClient.kt`**

```kotlin
package br.com.soat.messaging

import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import aws.sdk.kotlin.services.sns.model.MessageAttributeValue
import aws.sdk.kotlin.services.sns.model.PublishRequest

class SnsClient(private val topicArn: String) {
    private val client = AwsSnsClient { region = "us-east-1" }

    suspend fun publish(payload: String, eventType: String, messageId: String) {
        client.publish(PublishRequest {
            this.topicArn = this@SnsClient.topicArn
            message = payload
            messageDeduplicationId = messageId
            messageAttributes = mapOf(
                "event_type" to MessageAttributeValue {
                    dataType = "String"
                    stringValue = eventType
                }
            )
        })
    }
}
```

- [ ] **Step 3: Criar `SnsRelayEventHandler.kt`**

```kotlin
package br.com.soat.messaging

import br.com.soat.event.handler.EventHandler
import br.com.soat.event.model.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

class SnsRelayEventHandler(
    private val sns: SnsClient,
    private val mapper: ObjectMapper,
) : EventHandler<DomainEvent> {

    private val logger = LoggerFactory.getLogger(SnsRelayEventHandler::class.java)

    override suspend fun handle(event: DomainEvent) {
        val payload = mapper.writeValueAsString(event)
        sns.publish(
            payload = payload,
            eventType = event::class.simpleName ?: "Unknown",
            messageId = event.id.toString(),
        )
        logger.info("Published event ${event::class.simpleName}/${event.id} to SNS")
    }
}
```

- [ ] **Step 4: Registrar no DI (Koin) — modificar `main/.../AppModule.kt`**

```kotlin
single {
    SnsClient(topicArn = config.getString("messaging.sns.topic.arn"))
}

single<EventHandler<QuoteEmailRequestedEvent>>(named("QuoteEmailRequestedEvent.snsRelay")) {
    SnsRelayEventHandler(get(), get())
}
```

E adicionar no `application.yaml` o param `messaging.sns.topic.arn` (lido da env var `SNS_TOPIC_ARN` que vem do ConfigMap).

- [ ] **Step 5: Commit**

```bash
git add worker/src/main/kotlin/br/com/soat/messaging/ \
        worker/build.gradle.kts \
        main/src/main/kotlin/br/com/soat/config/AppModule.kt
git commit -m "feat(messaging): add SNS client and event handler relay; register in DI"
```

---

## Task 6: Publicar `QuoteEmailRequestedEvent` no fluxo de criação de OS

**Files:**
- Modify: handler de `OrderCreatedEvent` (provavelmente em `domain/src/main/kotlin/br/com/soat/order/event/handler/`)

- [ ] **Step 1: Localizar handler que disparava email**

```bash
grep -r "MailerSend\|sendEmail\|SendQuote" domain/ worker/ email/ --include='*.kt'
```

- [ ] **Step 2: Substituir chamada direta a MailerSend por publish do novo evento**

No handler relevante (provavelmente quando a OS muda pra status que aciona quote), substituir:
```kotlin
// ANTES:
emailService.sendQuote(order)

// DEPOIS:
eventPublisher.publish(QuoteEmailRequestedEvent(
    orderId = order.id,
    customerEmail = order.customer.email.value,
    customerName = order.customer.name,
    totalAmount = order.totalAmount,
    items = order.items.map { 
        QuoteEmailRequestedEvent.Item(it.description, it.unitPrice, it.quantity)
    },
))
```

- [ ] **Step 3: Remover deps do módulo `email/` em `build.gradle.kts` dos consumers**

- [ ] **Step 4: Rodar testes**

```bash
./gradlew test
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: replace direct MailerSend call with QuoteEmailRequestedEvent (outbox→SNS)"
```

---

## Task 7: Remover módulo `email/` inteiro

**Files:**
- Delete: `email/` directory
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Remover include do email em `settings.gradle.kts`**

```kotlin
// remover linha:
// include(":email")
```

- [ ] **Step 2: Remover dependência de `:email` em `main/build.gradle.kts` e outros**

```bash
grep -rn "project(\":email\")" .
```

Editar cada um pra remover.

- [ ] **Step 3: Deletar pasta**

```bash
rm -rf email/
```

- [ ] **Step 4: Build clean**

```bash
./gradlew clean build
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove email module (functionality moved to email-lambda)"
```

---

## Task 8: Micrometer + OTel + Logback JSON

**Files:**
- Modify: `api/build.gradle.kts` ou `main/build.gradle.kts`
- Create: `main/src/main/resources/logback.xml`
- Modify: `main/src/main/kotlin/br/com/soat/config/AppModule.kt` (Ktor plugins)

- [ ] **Step 1: Adicionar deps**

```kotlin
// api/build.gradle.kts (ou main)
dependencies {
    implementation("io.ktor:ktor-server-metrics-micrometer:3.3.3")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.1")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:2.5.0-alpha")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.39.0")
}
```

- [ ] **Step 2: Configurar Ktor**

Em `AppModule.kt` (ou onde Application está configurada):
```kotlin
val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
install(MicrometerMetrics) { registry = appMicrometerRegistry }
routing { get("/metrics") { call.respond(appMicrometerRegistry.scrape()) } }

// OTel
install(OpenTelemetry) {
    setTracerProvider(...)  // configurar via OTLP endpoint do collector
}
```

- [ ] **Step 3: Criar `logback.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

- [ ] **Step 4: Adicionar métricas de negócio**

No handler de criação de OS, depois do COMMIT:
```kotlin
private val ordersCreated = Counter.builder("orders_created_total")
    .description("Total de ordens de serviço criadas")
    .register(meterRegistry)

// ...
ordersCreated.increment()
```

E timers similares pra transição de status (criar `Timer`s registrados nos handlers de transição).

- [ ] **Step 5: Build + commit**

```bash
./gradlew build
git add -A
git commit -m "feat(observability): add Micrometer Prometheus, OTel Ktor, JSON logs with traceId"
```

---

## Task 9: Estrutura Kustomize `infra/k8s/base/`

**Files:**
- Reescrever: `infra/k8s/`

- [ ] **Step 1: Limpar diretório antigo**

```bash
rm -rf infra/k8s/
mkdir -p infra/k8s/base infra/k8s/overlays/hml infra/k8s/overlays/prod
```

- [ ] **Step 2: `infra/k8s/base/serviceaccount.yaml`**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: auto-repair-shop
  # annotation com IRSA é injetada via overlay (depende do env)
```

- [ ] **Step 3: `infra/k8s/base/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auto-repair-shop
spec:
  replicas: 2
  selector:
    matchLabels: { app: auto-repair-shop }
  template:
    metadata:
      labels: { app: auto-repair-shop }
    spec:
      serviceAccountName: auto-repair-shop
      containers:
        - name: auto-repair-shop
          image: ghcr.io/ivanzao/auto-repair-shop:latest
          ports:
            - containerPort: 8080
              name: http
          envFrom:
            - configMapRef: { name: auto-repair-shop-config }
            - secretRef:    { name: auto-repair-shop-secret }
          readinessProbe:
            httpGet: { path: /health, port: 8080 }
            initialDelaySeconds: 30
          livenessProbe:
            httpGet: { path: /health, port: 8080 }
            initialDelaySeconds: 60
          resources:
            requests: { cpu: 250m, memory: 512Mi }
            limits:   { memory: 1Gi }
```

- [ ] **Step 4: `infra/k8s/base/service.yaml` (NLB privado)**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auto-repair-shop-service
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-scheme: "internal"
    service.beta.kubernetes.io/aws-load-balancer-nlb-target-type: "ip"
spec:
  type: LoadBalancer
  selector: { app: auto-repair-shop }
  ports:
    - port: 8080
      targetPort: 8080
      protocol: TCP
```

- [ ] **Step 5: `infra/k8s/base/configmap.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: auto-repair-shop-config
data:
  APPLICATION_PORT: "8080"
  OTEL_ENDPOINT: "http://otel-collector.observability.svc.cluster.local:4317"
  # DATABASE_URL, SNS_TOPIC_ARN, APPLICATION_URL — virão de patches no overlay
```

- [ ] **Step 6: `infra/k8s/base/hpa.yaml`**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: { name: auto-repair-shop }
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auto-repair-shop
  minReplicas: 2
  maxReplicas: 4
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }
```

- [ ] **Step 7: `infra/k8s/base/servicemonitor.yaml`**

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: auto-repair-shop
  labels: { release: prom }
spec:
  selector:
    matchLabels: { app: auto-repair-shop }
  endpoints:
    - port: http
      path: /metrics
      interval: 30s
```

- [ ] **Step 8: `infra/k8s/base/kustomization.yaml`**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
commonLabels:
  app: auto-repair-shop
resources:
  - serviceaccount.yaml
  - deployment.yaml
  - service.yaml
  - configmap.yaml
  - hpa.yaml
  - servicemonitor.yaml
```

- [ ] **Step 9: Validar base**

```bash
kubectl kustomize infra/k8s/base/ > /dev/null
```

- [ ] **Step 10: Commit**

```bash
git add infra/k8s/base/
git commit -m "feat(k8s): add Kustomize base (deployment, service NLB, hpa, configmap, sa, servicemonitor)"
```

---

## Task 10: Overlays Kustomize hml e prod

**Files:**
- Create: `infra/k8s/overlays/hml/*`, `infra/k8s/overlays/prod/*`

- [ ] **Step 1: `infra/k8s/overlays/hml/kustomization.yaml`**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: auto-repair-shop-hml
resources:
  - ../../base
patchesStrategicMerge:
  - deployment-patch.yaml
  - configmap-patch.yaml
  - serviceaccount-patch.yaml
images:
  - name: ghcr.io/ivanzao/auto-repair-shop
    newTag: latest-hml
```

- [ ] **Step 2: `infra/k8s/overlays/hml/serviceaccount-patch.yaml`**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: auto-repair-shop
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/auto-repair-shop-app-hml
```

(O CI substitui `ACCOUNT_ID` em runtime.)

- [ ] **Step 3: `infra/k8s/overlays/hml/deployment-patch.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: auto-repair-shop }
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: auto-repair-shop
          resources:
            requests: { cpu: 100m, memory: 256Mi }
            limits:   { memory: 512Mi }
```

- [ ] **Step 4: `infra/k8s/overlays/hml/configmap-patch.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata: { name: auto-repair-shop-config }
data:
  ENV: hml
  DATABASE_NAME: auto_repair_shop_hml
  # DATABASE_URL e SNS_TOPIC_ARN são patched em runtime pelo CI (vêm de SSM)
```

- [ ] **Step 5: Idem para `overlays/prod/` (replicas: 2-3, resources maiores, ENV: prod, DATABASE_NAME: auto_repair_shop_prod)**

- [ ] **Step 6: Validar overlays**

```bash
kubectl kustomize infra/k8s/overlays/hml/ > /dev/null
kubectl kustomize infra/k8s/overlays/prod/ > /dev/null
```

- [ ] **Step 7: Commit**

```bash
git add infra/k8s/overlays/
git commit -m "feat(k8s): add Kustomize overlays for hml and prod"
```

---

## Task 11: Reescrever `.github/workflows/deploy.yaml`

**Files:**
- Modify: `.github/workflows/deploy.yaml`

- [ ] **Step 1: Substituir pelo novo workflow**

```yaml
name: Build & Deploy

on:
  push:
    branches: [main, develop]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}
  AWS_REGION: us-east-1

jobs:
  build:
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :main:shadowJar
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GHCR_PAT }}
      - id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: type=sha,prefix=sha-,format=short
      - uses: docker/build-push-action@v6
        with:
          context: .
          target: production
          push: true
          tags: ${{ steps.meta.outputs.tags }}

  deploy:
    runs-on: ubuntu-latest
    needs: build
    env:
      ENV: ${{ github.ref_name == 'main' && 'prod' || 'hml' }}
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Read SSM outputs
        id: ssm
        run: |
          CLUSTER=$(aws ssm get-parameter --name /auto-repair-shop/eks/cluster-name --query Parameter.Value --output text)
          DB_HOST=$(aws ssm get-parameter --name /auto-repair-shop/db/endpoint --query Parameter.Value --output text)
          DB_NAME=$(aws ssm get-parameter --name /auto-repair-shop/${{ env.ENV }}/db/name --query Parameter.Value --output text)
          DB_USER=$(aws ssm get-parameter --name /auto-repair-shop/${{ env.ENV }}/db/username --query Parameter.Value --output text)
          DB_SECRET_ARN=$(aws ssm get-parameter --name /auto-repair-shop/${{ env.ENV }}/db/secret-arn --query Parameter.Value --output text)
          SNS_TOPIC=$(aws ssm get-parameter --name /auto-repair-shop/${{ env.ENV }}/sns/events-topic-arn --query Parameter.Value --output text)
          ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
          echo "cluster=$CLUSTER" >> $GITHUB_OUTPUT
          echo "db_host=$DB_HOST" >> $GITHUB_OUTPUT
          echo "db_name=$DB_NAME" >> $GITHUB_OUTPUT
          echo "db_user=$DB_USER" >> $GITHUB_OUTPUT
          echo "db_secret_arn=$DB_SECRET_ARN" >> $GITHUB_OUTPUT
          echo "sns_topic=$SNS_TOPIC" >> $GITHUB_OUTPUT
          echo "account=$ACCOUNT" >> $GITHUB_OUTPUT

      - run: aws eks update-kubeconfig --name ${{ steps.ssm.outputs.cluster }} --region ${{ env.AWS_REGION }}

      - name: Sync DB password from Secrets Manager → K8s Secret
        run: |
          DB_PWD=$(aws secretsmanager get-secret-value --secret-id ${{ steps.ssm.outputs.db_secret_arn }} \
                   --query SecretString --output text | jq -r .password)
          kubectl create secret generic auto-repair-shop-secret \
            --namespace=auto-repair-shop-${{ env.ENV }} \
            --from-literal="DATABASE_PASSWORD=$DB_PWD" \
            --dry-run=client -o yaml | kubectl apply -f -

      - name: Patch ConfigMap with runtime values
        working-directory: infra/k8s/overlays/${{ env.ENV }}
        run: |
          cat >> configmap-patch.yaml <<EOF
          
            DATABASE_URL: "jdbc:postgresql://${{ steps.ssm.outputs.db_host }}:5432/${{ steps.ssm.outputs.db_name }}"
            DATABASE_USERNAME: "${{ steps.ssm.outputs.db_user }}"
            SNS_TOPIC_ARN: "${{ steps.ssm.outputs.sns_topic }}"
          EOF

      - name: Substitute ACCOUNT_ID in serviceaccount-patch
        run: |
          sed -i "s/ACCOUNT_ID/${{ steps.ssm.outputs.account }}/" infra/k8s/overlays/${{ env.ENV }}/serviceaccount-patch.yaml

      - name: Set image tag
        working-directory: infra/k8s/overlays/${{ env.ENV }}
        run: |
          kustomize edit set image \
            ghcr.io/ivanzao/auto-repair-shop=ghcr.io/${{ github.repository }}:sha-${GITHUB_SHA::7}

      - name: Apply
        run: kubectl apply -k infra/k8s/overlays/${{ env.ENV }}

      - run: kubectl rollout status deployment/auto-repair-shop -n auto-repair-shop-${{ env.ENV }} --timeout=300s

      - name: Smoke test via API Gateway
        run: |
          APIGW=$(aws ssm get-parameter --name /auto-repair-shop/${{ env.ENV }}/apigw/endpoint --query Parameter.Value --output text)
          for i in $(seq 1 10); do
            CODE=$(curl -s -o /dev/null -w "%{http_code}" "$APIGW/v1/health")
            [ "$CODE" = "200" ] && exit 0
            sleep 5
          done
          exit 1
```

- [ ] **Step 2: Atualizar `pr-check.yaml`**

Adicionar lint Kustomize:
```yaml
      - name: Kustomize lint
        run: |
          kubectl kustomize infra/k8s/overlays/hml > /dev/null
          kubectl kustomize infra/k8s/overlays/prod > /dev/null
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/
git commit -m "ci: rewrite deploy to use Kustomize overlays, SSM-driven config, smoke test via API GW"
```

---

## Task 12: README atualizado

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Atualizar seções de arquitetura (header auth, outbox, Kustomize) + link pro docs/architecture/**

Adicionar seções:
- "Authentication": app não valida JWT; confia em `X-User-Id`/`X-User-Role` injetados pelo API Gateway (via Lambda Authorizer)
- "Event outbox": eventos com flag `external` vão pra SNS via `SnsRelayEventHandler` ao serem processados pelo `EventProcessorTask`
- "Deploy": Kustomize com overlays hml/prod; CI lê valores dinâmicos do SSM
- Diagramas: link pra `docs/architecture/`

- [ ] **Step 2: Commit final**

```bash
git add README.md
git commit -m "docs: update README for new architecture (header auth, outbox→SNS, Kustomize)"
```

---

## Task 13: Push e validação end-to-end

- [ ] **Step 1: Criar branch `develop` se não existir, fazer push em `develop`**

```bash
git checkout -b develop 2>/dev/null || git checkout develop
git merge main
git push -u origin develop
```

- [ ] **Step 2: Branch protection no GitHub**

```bash
gh api -X PUT repos/ivanzao/auto-repair-shop/branches/develop/protection \
  -F required_status_checks[strict]=true \
  -F enforce_admins=true \
  -F required_pull_request_reviews[required_approving_review_count]=0 \
  -f restrictions=null
```

- [ ] **Step 3: Aguardar deploy + validar**

```bash
APIGW_HML=$(aws ssm get-parameter --name /auto-repair-shop/hml/apigw/endpoint --query Parameter.Value --output text)
curl "$APIGW_HML/v1/health"  # 200
curl "$APIGW_HML/v1/orders"  # 401 sem token

# Login pra pegar token (precisa de user real no DB)
curl -X POST "$APIGW_HML/auth/login" -H 'Content-Type: application/json' \
  -d '{"cpf":"<CPF-real>","password":"<senha-real>"}' | jq -r .token

# Listar orders com token
curl "$APIGW_HML/v1/orders" -H "Authorization: Bearer <token>"  # 200
```

- [ ] **Step 4: Validar fluxo de email**

Criar OS via API → ver logs do app → confirmar evento em `events` table com status PROCESSED → ver invocação no Lambda email no CloudWatch → email recebido.

---

## Critérios de conclusão deste plano

- [ ] App boota com header auth ativo (testes passam)
- [ ] `POST /v1/orders` sem `X-User-Id` retorna 401 (em chamada direta ao app, bypassing API GW pra teste)
- [ ] App boota e expõe `/metrics` (Prometheus scrape funciona)
- [ ] Logs em JSON com `traceId` aparecem no Loki via Promtail
- [ ] Criar OS dispara `QuoteEmailRequestedEvent` que vai pra `events` table com `status=PENDING`
- [ ] `EventProcessorTask` processa o evento, publica no SNS, marca como `PROCESSED`
- [ ] Email Lambda consome o evento do SQS e envia pelo MailerSend
- [ ] Email é recebido na caixa do destinatário
- [ ] `kubectl get pods -n auto-repair-shop-hml` mostra app Running
- [ ] HPA escala entre 1 e 2 pods em hml; 2 e 4 em prod
- [ ] Dashboards Grafana de Operação mostram contadores de OS
- [ ] Trace de uma request aparece no Tempo (Lambda authorizer + App + DB)
- [ ] Branch `main` e `develop` protegidas; PRs obrigatórios
