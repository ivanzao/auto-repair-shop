# SNS Command Refactor + Auth Bearer + MetricsPort — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finalizar a refatoração cloud do `auto-repair-shop`: o que vai pra SNS é Command (não Event); App drop User → Attendant + JWT Bearer (Lambda Authorizer); MetricsPort isola Micrometer; tag `latest` único nos overlays; LocalStack valida publicação SNS no integration test de lifecycle.

**Architecture:** Mantém arquitetura hexagonal multi-módulo. Domain não conhece infraestrutura. Outbox interno (`commands` table) + `CommandProcessor` + `SnsRelayCommandHandler` publica em SNS. Auth no app só decoda Bearer JWT (sem revalidar — Lambda Authorizer já fez). LocalStack via Testcontainers no integration test.

**Tech Stack:** Kotlin 2.2 + Ktor 3.3 + Koin 4.1 + Exposed 0.61 + Flyway 11 + Micrometer 1.13 + AWS SDK Kotlin 1.5 (SNS + SQS) + Testcontainers 1.21 (LocalStack + Postgres) + Jackson 2.20.

**Git policy:** O usuário cuida de todos os commits manualmente. **Não executar nenhum comando git** ao longo da execução. "Logical commit point" é só uma marcação para o usuário decidir quando commitar.

**Spec base:** `docs/superpowers/specs/2026-05-23-sns-command-refactor-design.md`.

---

## Pre-flight checks

Antes de começar, validar baseline:

- [ ] **Rodar build atual (deve estar verde):**

```
./gradlew test integrationTest
```
Se quebrar antes de qualquer mudança, parar e investigar — não é problema do plan.

- [ ] **Confirmar working directories:** estamos em `/home/ivanzao/dev/repository/auto-repair-shop`. O repo `auto-repair-shop-infra` **não é tocado** neste plan.

---

## Task 1: Trocar `latest-hml`/`latest-prod` por `latest` nos overlays

Mudança trivial, sem teste — começa pelo aquecimento.

**Files:**
- Modify: `infra/k8s/overlays/hml/kustomization.yaml`
- Modify: `infra/k8s/overlays/prod/kustomization.yaml`

- [ ] **Step 1: Trocar `newTag` em HML**

Em `infra/k8s/overlays/hml/kustomization.yaml`, na linha 15, trocar:

```yaml
    newTag: latest-hml
```
por:
```yaml
    newTag: latest
```

- [ ] **Step 2: Trocar `newTag` em PROD**

Em `infra/k8s/overlays/prod/kustomization.yaml`, na linha 15, trocar:

```yaml
    newTag: latest-prod
```
por:
```yaml
    newTag: latest
```

- [ ] **Step 3: Validar com `kustomize build`**

Run:
```
kustomize build infra/k8s/overlays/hml | grep "image:"
kustomize build infra/k8s/overlays/prod | grep "image:"
```
Expected: ambas as linhas mostrando `ghcr.io/ivanzao/auto-repair-shop:latest` (sem `-hml`/`-prod`).

Se `kustomize` não estiver instalado, este passo é opcional — o CI valida no PR check.

- [ ] **Logical commit point:** "chore(k8s): use single `latest` tag in overlays"

---

## Task 2: Criar módulo `metric/` com `MetricsPort` no domain

**Files:**
- Modify: `settings.gradle.kts`
- Create: `metric/build.gradle.kts`
- Create: `domain/src/main/kotlin/br/com/soat/metric/MetricsPort.kt`

- [ ] **Step 1: Incluir módulo no Gradle**

Em `settings.gradle.kts`, adicionar:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "auto-repair-shop"

include("main")
include("domain")
include("api")
include("storage")
include("worker")
include("metric")
```

- [ ] **Step 2: Criar `metric/build.gradle.kts`**

Conteúdo completo:

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.slf4j.api)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Criar `MetricsPort` no domain**

Caminho: `domain/src/main/kotlin/br/com/soat/metric/MetricsPort.kt`

```kotlin
package br.com.soat.metric

interface MetricsPort {

    fun counter(
        name: String,
        description: String = "",
        tags: Map<String, String> = emptyMap(),
    ): Counter

    fun timer(
        name: String,
        description: String = "",
        tags: Map<String, String> = emptyMap(),
    ): Timer

    interface Counter {
        fun increment(amount: Double = 1.0)
    }

    interface Timer {
        fun record(durationMs: Long)
        fun <T> recordSupplier(block: () -> T): T
    }
}
```

- [ ] **Step 4: Rodar build do módulo metric (vazio ainda, mas compila)**

Run:
```
./gradlew :metric:compileKotlin :domain:compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "feat(metric): scaffold metric module with MetricsPort interface in domain"

---

## Task 3: Implementar `MicrometerMetricsPort` no módulo `metric/`

**Files:**
- Create: `metric/src/main/kotlin/br/com/soat/metric/MicrometerMetricsPort.kt`
- Create: `metric/src/test/kotlin/br/com/soat/metric/MicrometerMetricsPortTest.kt`

- [ ] **Step 1: Escrever teste falhando**

Caminho: `metric/src/test/kotlin/br/com/soat/metric/MicrometerMetricsPortTest.kt`

```kotlin
package br.com.soat.metric

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MicrometerMetricsPortTest {

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val port = MicrometerMetricsPort(registry)

    @Test
    fun `counter increments the underlying micrometer counter`() {
        val counter = port.counter("test_counter_total", "desc", mapOf("kind" to "unit"))
        counter.increment()
        counter.increment(3.0)

        val mc = registry.find("test_counter_total").tag("kind", "unit").counter()!!
        assertEquals(4.0, mc.count())
    }

    @Test
    fun `timer records duration`() {
        val timer = port.timer("test_timer_seconds", "desc")
        timer.record(150)

        val mt = registry.find("test_timer_seconds").timer()!!
        assertEquals(1L, mt.count())
    }

    @Test
    fun `timer recordSupplier returns the supplier result`() {
        val timer = port.timer("test_timer_supplier", "desc")
        val result = timer.recordSupplier { 42 }
        assertEquals(42, result)
    }
}
```

- [ ] **Step 2: Rodar teste, ver falhar**

Run:
```
./gradlew :metric:test --tests "br.com.soat.metric.MicrometerMetricsPortTest"
```
Expected: FAIL — `unresolved reference: MicrometerMetricsPort`.

- [ ] **Step 3: Implementar `MicrometerMetricsPort`**

Caminho: `metric/src/main/kotlin/br/com/soat/metric/MicrometerMetricsPort.kt`

```kotlin
package br.com.soat.metric

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.Duration

class MicrometerMetricsPort(private val registry: MeterRegistry) : MetricsPort {

    override fun counter(name: String, description: String, tags: Map<String, String>): MetricsPort.Counter {
        val counter = io.micrometer.core.instrument.Counter
            .builder(name)
            .description(description)
            .tags(tags.toMicrometerTags())
            .register(registry)
        return MicrometerCounter(counter)
    }

    override fun timer(name: String, description: String, tags: Map<String, String>): MetricsPort.Timer {
        val timer = io.micrometer.core.instrument.Timer
            .builder(name)
            .description(description)
            .tags(tags.toMicrometerTags())
            .register(registry)
        return MicrometerTimer(timer)
    }

    private class MicrometerCounter(private val mc: io.micrometer.core.instrument.Counter) : MetricsPort.Counter {
        override fun increment(amount: Double) = mc.increment(amount)
    }

    private class MicrometerTimer(private val mt: io.micrometer.core.instrument.Timer) : MetricsPort.Timer {
        override fun record(durationMs: Long) = mt.record(Duration.ofMillis(durationMs))
        override fun <T> recordSupplier(block: () -> T): T = mt.recordCallable(block)!!
    }

    private fun Map<String, String>.toMicrometerTags(): List<Tag> =
        map { Tag.of(it.key, it.value) }
}
```

- [ ] **Step 4: Rodar teste, ver passar**

Run:
```
./gradlew :metric:test --tests "br.com.soat.metric.MicrometerMetricsPortTest"
```
Expected: PASS (3 tests).

- [ ] **Logical commit point:** "feat(metric): implement MicrometerMetricsPort adapter"

---

## Task 4: Refatorar `OrderUseCase` para usar `MetricsPort`

`OrderUseCase` hoje importa `io.micrometer.core.instrument.{Counter,MeterRegistry}` — domain não pode conhecer Micrometer.

**Files:**
- Modify: `domain/src/main/kotlin/br/com/soat/order/OrderUseCase.kt`
- Modify: `main/src/main/kotlin/br/com/soat/Main.kt` (Koin)
- Modify: `main/build.gradle.kts` (depender de `metric`)
- Test: validar via `./gradlew :main:integrationTest` (passa pelo `OrderLifecycleIntegrationTest` existente)

- [ ] **Step 1: Adicionar dependência do módulo `metric` no `main`**

Em `main/build.gradle.kts`, dentro do bloco `dependencies`, adicionar (após `implementation(project(":worker"))`):

```kotlin
    implementation(project(":metric"))
```

- [ ] **Step 2: Trocar imports e tipo no `OrderUseCase`**

Em `domain/src/main/kotlin/br/com/soat/order/OrderUseCase.kt`:

Remover linhas:
```kotlin
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
```

Adicionar:
```kotlin
import br.com.soat.metric.MetricsPort
```

Trocar o parâmetro do construtor:
```kotlin
    meterRegistry: MeterRegistry,
```
por:
```kotlin
    metrics: MetricsPort,
```

Trocar o counter:
```kotlin
    private val ordersCreated: Counter = Counter.builder("orders_created_total")
        .description("Total de ordens de serviço criadas")
        .register(meterRegistry)
```
por:
```kotlin
    private val ordersCreated: MetricsPort.Counter = metrics.counter(
        name = "orders_created_total",
        description = "Total de ordens de serviço criadas",
    )
```

- [ ] **Step 3: Atualizar Koin no `Main.kt`**

Em `main/src/main/kotlin/br/com/soat/Main.kt`, adicionar import:
```kotlin
import br.com.soat.metric.MetricsPort
import br.com.soat.metric.MicrometerMetricsPort
```

Dentro de `applicationModule`, **abaixo** das linhas:
```kotlin
    single<PrometheusMeterRegistry> { prometheusMeterRegistry() }
    single<MeterRegistry> { get<PrometheusMeterRegistry>() }
```
adicionar:
```kotlin
    single<MetricsPort> { MicrometerMetricsPort(get<MeterRegistry>()) }
```

Confirmar que o `get()` que injeta no `OrderUseCase` agora vai resolver `MetricsPort` em vez de `MeterRegistry`. A linha:
```kotlin
    single<OrderUseCase> { OrderUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
```
permanece igual — Koin resolve por tipo no construtor.

- [ ] **Step 4: Validar que domain não conhece Micrometer**

Run:
```
grep -r "io.micrometer" domain/src/main/kotlin || echo OK
```
Expected: `OK`.

- [ ] **Step 5: Rodar build completo**

Run:
```
./gradlew build
```
Expected: BUILD SUCCESSFUL. Se quebrar com "MeterRegistry not found" em algum teste do domain, ajustar o teste para usar um fake `MetricsPort` (ver passo 6).

- [ ] **Step 6: Ajustar testes do `OrderUseCase` (se houver)**

Run:
```
grep -rln "MeterRegistry\|meterRegistry" domain/src/test main/src/test 2>/dev/null
```
Para cada arquivo listado, trocar:
```kotlin
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
```
por um fake inline (ou criar em `domain/src/test/kotlin/br/com/soat/metric/FakeMetricsPort.kt`):

```kotlin
package br.com.soat.metric

class FakeMetricsPort : MetricsPort {
    val counters = mutableMapOf<String, Double>()

    override fun counter(name: String, description: String, tags: Map<String, String>) =
        object : MetricsPort.Counter {
            override fun increment(amount: Double) {
                counters.merge(name, amount, Double::plus)
            }
        }

    override fun timer(name: String, description: String, tags: Map<String, String>) =
        object : MetricsPort.Timer {
            override fun record(durationMs: Long) {}
            override fun <T> recordSupplier(block: () -> T): T = block()
        }
}
```

E nos testes, trocar `meterRegistry = SimpleMeterRegistry()` por `metrics = FakeMetricsPort()`.

- [ ] **Step 7: Rodar testes**

Run:
```
./gradlew test
```
Expected: PASS.

- [ ] **Logical commit point:** "refactor(domain): replace MeterRegistry in OrderUseCase with MetricsPort"

---

## Task 5: Substituir `HeaderAuthenticationProvider` por `JwtBearerAuthenticationProvider`

App passa a ler `Authorization: Bearer <jwt>` e decoda claims sem validar assinatura (Lambda Authorizer já validou).

**Files:**
- Create: `api/src/main/kotlin/br/com/soat/auth/JwtBearerAuthenticationProvider.kt`
- Modify: `api/src/main/kotlin/br/com/soat/config/AuthenticationConfiguration.kt`
- Delete: `api/src/main/kotlin/br/com/soat/auth/HeaderAuthenticationProvider.kt`
- Test: `api/src/test/kotlin/br/com/soat/auth/JwtBearerAuthenticationProviderTest.kt` (unit) + cobertura via integration tests (Task 16)

- [ ] **Step 1: Escrever teste falhando do parser de JWT**

Caminho: `api/src/test/kotlin/br/com/soat/auth/JwtClaimsTest.kt`

```kotlin
package br.com.soat.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class JwtClaimsTest {

    private fun jwt(payload: String): String {
        val header = base64UrlEncode("""{"alg":"HS512","typ":"JWT"}""")
        val body = base64UrlEncode(payload)
        return "$header.$body.signature_not_validated"
    }

    private fun base64UrlEncode(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test
    fun `parses sub and role from JWT payload`() {
        val token = jwt("""{"sub":"d2d2c1e4-1111-2222-3333-aaaabbbbcccc","role":"ATTENDANT","exp":1234}""")

        val claims = JwtClaims.parse(token)

        assertEquals("d2d2c1e4-1111-2222-3333-aaaabbbbcccc", claims!!.sub)
        assertEquals("ATTENDANT", claims.role)
    }

    @Test
    fun `returns null when token has wrong number of segments`() {
        assertNull(JwtClaims.parse("just.two"))
        assertNull(JwtClaims.parse("only-one"))
    }

    @Test
    fun `returns null when payload is not valid base64`() {
        assertNull(JwtClaims.parse("header.@@@not-base64@@@.sig"))
    }

    @Test
    fun `returns null when payload is missing sub or role`() {
        val noSub = jwt("""{"role":"ADMIN"}""")
        val noRole = jwt("""{"sub":"abc"}""")
        assertNull(JwtClaims.parse(noSub))
        assertNull(JwtClaims.parse(noRole))
    }
}
```

- [ ] **Step 2: Rodar teste, ver falhar**

Run:
```
./gradlew :api:test --tests "br.com.soat.auth.JwtClaimsTest"
```
Expected: FAIL — `unresolved reference: JwtClaims`.

- [ ] **Step 3: Implementar `JwtClaims`**

Caminho: `api/src/main/kotlin/br/com/soat/auth/JwtClaims.kt`

```kotlin
package br.com.soat.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64

data class JwtClaims(val sub: String, val role: String) {

    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()

        fun parse(token: String): JwtClaims? {
            val parts = token.split('.')
            if (parts.size != 3) return null

            val payloadJson = try {
                val bytes = Base64.getUrlDecoder().decode(parts[1])
                String(bytes)
            } catch (e: IllegalArgumentException) {
                return null
            }

            val node = try {
                mapper.readTree(payloadJson)
            } catch (e: Exception) {
                return null
            }

            val sub = node.get("sub")?.asText()?.takeIf { it.isNotBlank() } ?: return null
            val role = node.get("role")?.asText()?.takeIf { it.isNotBlank() } ?: return null

            return JwtClaims(sub, role)
        }
    }
}
```

- [ ] **Step 4: Rodar teste, ver passar**

Run:
```
./gradlew :api:test --tests "br.com.soat.auth.JwtClaimsTest"
```
Expected: PASS (4 tests).

- [ ] **Step 5: Criar `JwtBearerAuthenticationProvider` + `JwtUserPrincipal`**

Caminho: `api/src/main/kotlin/br/com/soat/auth/JwtBearerAuthenticationProvider.kt`

```kotlin
package br.com.soat.auth

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import java.util.UUID

data class JwtUserPrincipal(val userId: UUID, val role: String)

class JwtBearerAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {

    private val allowedRoles: Set<String> = config.allowedRoles

    class Config(name: String?) : AuthenticationProvider.Config(name) {
        var allowedRoles: Set<String> = emptySet()
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val header = call.request.parseAuthorizationHeader()
        val token = (header as? HttpAuthHeader.Single)
            ?.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }
            ?.blob

        if (token.isNullOrBlank()) {
            context.challenge("JwtBearer", AuthenticationFailedCause.NoCredentials) { challenge, c ->
                c.respond(HttpStatusCode.Unauthorized)
                challenge.complete()
            }
            return
        }

        val claims = JwtClaims.parse(token)
        if (claims == null) {
            context.challenge("JwtBearer", AuthenticationFailedCause.InvalidCredentials) { challenge, c ->
                c.respond(HttpStatusCode.Unauthorized)
                challenge.complete()
            }
            return
        }

        val userId = try {
            UUID.fromString(claims.sub)
        } catch (e: IllegalArgumentException) {
            context.challenge("JwtBearer", AuthenticationFailedCause.InvalidCredentials) { challenge, c ->
                c.respond(HttpStatusCode.Unauthorized)
                challenge.complete()
            }
            return
        }

        if (allowedRoles.isNotEmpty() && claims.role !in allowedRoles) {
            context.challenge("JwtBearer", AuthenticationFailedCause.InvalidCredentials) { challenge, c ->
                c.respond(HttpStatusCode.Forbidden)
                challenge.complete()
            }
            return
        }

        context.principal(JwtUserPrincipal(userId, claims.role))
    }
}

fun AuthenticationConfig.jwtBearer(
    name: String? = null,
    configure: JwtBearerAuthenticationProvider.Config.() -> Unit = {},
) {
    val provider = JwtBearerAuthenticationProvider(
        JwtBearerAuthenticationProvider.Config(name).apply(configure),
    )
    register(provider)
}
```

- [ ] **Step 6: Atualizar `AuthenticationConfiguration`**

Em `api/src/main/kotlin/br/com/soat/config/AuthenticationConfiguration.kt`, substituir o conteúdo por:

```kotlin
package br.com.soat.config

import br.com.soat.auth.jwtBearer
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication

fun Application.configureAuthentication() {
    install(Authentication) {
        jwtBearer("admin") {
            allowedRoles = setOf("ADMIN")
        }
        jwtBearer("attendant") {
            allowedRoles = setOf("ADMIN", "ATTENDANT")
        }
    }
}
```

- [ ] **Step 7: Apagar `HeaderAuthenticationProvider`**

```
rm api/src/main/kotlin/br/com/soat/auth/HeaderAuthenticationProvider.kt
```

- [ ] **Step 8: Compilar**

Run:
```
./gradlew :api:compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "feat(api): authenticate via Authorization Bearer JWT, drop HeaderAuthenticationProvider"

---

## Task 6: Criar `Attendant` domain (model + use case + repository port)

**Files:**
- Create: `domain/src/main/kotlin/br/com/soat/attendant/model/Attendant.kt`
- Create: `domain/src/main/kotlin/br/com/soat/attendant/model/CreateAttendantRequest.kt`
- Create: `domain/src/main/kotlin/br/com/soat/attendant/model/UpdateAttendantRequest.kt`
- Create: `domain/src/main/kotlin/br/com/soat/attendant/exception/AttendantExceptions.kt`
- Create: `domain/src/main/kotlin/br/com/soat/attendant/AttendantRepository.kt`
- Create: `domain/src/main/kotlin/br/com/soat/attendant/AttendantUseCase.kt`
- Create: `domain/src/test/kotlin/br/com/soat/attendant/AttendantUseCaseTest.kt`

- [ ] **Step 1: Modelo `Attendant`**

Caminho: `domain/src/main/kotlin/br/com/soat/attendant/model/Attendant.kt`

```kotlin
package br.com.soat.attendant.model

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class Attendant(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val version: Int = 0,

    val name: String,
    val document: Document,
    val email: Email,
    val contact: PhoneNumber,
)
```

- [ ] **Step 2: Modelos de request**

`domain/src/main/kotlin/br/com/soat/attendant/model/CreateAttendantRequest.kt`:

```kotlin
package br.com.soat.attendant.model

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class CreateAttendantRequest(
    val name: String,
    val document: Document,
    val email: Email,
    val contact: PhoneNumber,
)
```

`domain/src/main/kotlin/br/com/soat/attendant/model/UpdateAttendantRequest.kt`:

```kotlin
package br.com.soat.attendant.model

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class UpdateAttendantRequest(
    val name: String,
    val document: Document,
    val email: Email,
    val contact: PhoneNumber,
)
```

- [ ] **Step 3: Exceptions**

`domain/src/main/kotlin/br/com/soat/attendant/exception/AttendantExceptions.kt`:

```kotlin
package br.com.soat.attendant.exception

import java.util.UUID

class AttendantNotFoundException(id: UUID) : RuntimeException("Attendant not found: $id")
class AttendantAlreadyExistsException : RuntimeException("Attendant with this document already exists")
```

- [ ] **Step 4: Repository port**

`domain/src/main/kotlin/br/com/soat/attendant/AttendantRepository.kt`:

```kotlin
package br.com.soat.attendant

import br.com.soat.attendant.model.Attendant
import br.com.soat.shared.vo.Document
import java.util.UUID

interface AttendantRepository {
    fun create(attendant: Attendant): Attendant
    fun update(attendant: Attendant): Attendant
    fun delete(id: UUID)
    fun findById(id: UUID): Attendant?
    fun findByDocument(document: Document): Attendant?
    fun findAll(): List<Attendant>
}
```

- [ ] **Step 5: Escrever teste falhando do `AttendantUseCase`**

`domain/src/test/kotlin/br/com/soat/attendant/AttendantUseCaseTest.kt`:

```kotlin
package br.com.soat.attendant

import br.com.soat.attendant.exception.AttendantAlreadyExistsException
import br.com.soat.attendant.exception.AttendantNotFoundException
import br.com.soat.attendant.model.Attendant
import br.com.soat.attendant.model.CreateAttendantRequest
import br.com.soat.attendant.model.UpdateAttendantRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.UUID.randomUUID

class AttendantUseCaseTest {

    private lateinit var repo: InMemoryAttendantRepository
    private lateinit var useCase: AttendantUseCase

    @BeforeEach
    fun setup() {
        repo = InMemoryAttendantRepository()
        useCase = AttendantUseCase(repo)
    }

    @Test
    fun `create persists attendant when document is unique`() {
        val req = CreateAttendantRequest(
            name = "Alice",
            document = Document("12345678901"),
            email = Email("alice@example.com"),
            contact = PhoneNumber("11999990000"),
        )

        val created = useCase.create(req)

        assertEquals("Alice", created.name)
        assertEquals(req.document, created.document)
        assertEquals(1, repo.findAll().size)
    }

    @Test
    fun `create throws when document already exists`() {
        val req = CreateAttendantRequest(
            name = "Alice",
            document = Document("12345678901"),
            email = Email("alice@example.com"),
            contact = PhoneNumber("11999990000"),
        )
        useCase.create(req)

        assertThrows(AttendantAlreadyExistsException::class.java) { useCase.create(req) }
    }

    @Test
    fun `findById throws when not found`() {
        assertThrows(AttendantNotFoundException::class.java) {
            useCase.findById(randomUUID())
        }
    }

    @Test
    fun `update mutates name email document contact`() {
        val created = useCase.create(
            CreateAttendantRequest(
                "Alice", Document("12345678901"), Email("a@x.com"), PhoneNumber("11999990000")
            )
        )

        val updated = useCase.update(
            created.id,
            UpdateAttendantRequest("Bob", Document("22233344455"), Email("b@x.com"), PhoneNumber("11888880000"))
        )

        assertEquals("Bob", updated.name)
        assertEquals(Document("22233344455"), updated.document)
    }

    @Test
    fun `delete removes attendant`() {
        val created = useCase.create(
            CreateAttendantRequest(
                "Alice", Document("12345678901"), Email("a@x.com"), PhoneNumber("11999990000")
            )
        )
        useCase.delete(created.id)
        assertEquals(0, repo.findAll().size)
    }
}

private class InMemoryAttendantRepository : AttendantRepository {
    private val store = mutableMapOf<UUID, Attendant>()

    override fun create(attendant: Attendant): Attendant {
        store[attendant.id] = attendant
        return attendant
    }

    override fun update(attendant: Attendant): Attendant {
        store[attendant.id] = attendant
        return attendant
    }

    override fun delete(id: UUID) { store.remove(id) }
    override fun findById(id: UUID): Attendant? = store[id]
    override fun findByDocument(document: Document): Attendant? = store.values.firstOrNull { it.document == document }
    override fun findAll(): List<Attendant> = store.values.toList()
}
```

- [ ] **Step 6: Rodar teste, ver falhar**

Run:
```
./gradlew :domain:test --tests "br.com.soat.attendant.AttendantUseCaseTest"
```
Expected: FAIL — `unresolved reference: AttendantUseCase`.

- [ ] **Step 7: Implementar `AttendantUseCase`**

`domain/src/main/kotlin/br/com/soat/attendant/AttendantUseCase.kt`:

```kotlin
package br.com.soat.attendant

import br.com.soat.attendant.exception.AttendantAlreadyExistsException
import br.com.soat.attendant.exception.AttendantNotFoundException
import br.com.soat.attendant.model.Attendant
import br.com.soat.attendant.model.CreateAttendantRequest
import br.com.soat.attendant.model.UpdateAttendantRequest
import java.util.UUID

class AttendantUseCase(
    private val repository: AttendantRepository,
) {

    fun findById(id: UUID): Attendant =
        repository.findById(id) ?: throw AttendantNotFoundException(id)

    fun findAll(): List<Attendant> = repository.findAll()

    fun create(request: CreateAttendantRequest): Attendant {
        repository.findByDocument(request.document)?.let { throw AttendantAlreadyExistsException() }

        return repository.create(
            Attendant(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact,
            )
        )
    }

    fun update(id: UUID, request: UpdateAttendantRequest): Attendant {
        val existing = repository.findById(id) ?: throw AttendantNotFoundException(id)
        return repository.update(
            existing.copy(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact,
            )
        )
    }

    fun delete(id: UUID) {
        repository.delete(id)
    }
}
```

- [ ] **Step 8: Rodar teste, ver passar**

Run:
```
./gradlew :domain:test --tests "br.com.soat.attendant.AttendantUseCaseTest"
```
Expected: PASS (5 tests).

- [ ] **Logical commit point:** "feat(domain): introduce Attendant aggregate with CRUD use case"

---

## Task 7: Flyway migration — DROP `users`, CREATE `attendants`

**Files:**
- Create: `storage/src/main/resources/db/migration/V13__rename_users_to_attendants.sql`

- [ ] **Step 1: Confirmar nome da FK existente em `orders`**

Run:
```
grep -n "attendant" storage/src/main/resources/db/migration/V3__create_order_tables.sql
```
Expected: ver linha com `FOREIGN KEY (attendant_id) REFERENCES users(id)` (ou similar). Anotar o nome da constraint (default Postgres é `orders_attendant_id_fkey`).

- [ ] **Step 2: Criar V13 migration**

Caminho: `storage/src/main/resources/db/migration/V13__rename_users_to_attendants.sql`

```sql
-- HML/PROD atualmente sem dados (Sec 3 do spec): migration assume tabelas vazias.

-- Drop FK em orders → users (nome convencional do Postgres)
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_attendant_id_fkey;

-- App não é mais dono de users (Lambda admin terá migration própria)
DROP TABLE IF EXISTS users CASCADE;

-- Cria attendants enxuto (sem hashed_password, sem role)
CREATE TABLE attendants (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    document     VARCHAR(20)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    contact      VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    modified_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version      INTEGER      NOT NULL DEFAULT 0
);

-- Restaura FK orders → attendants
ALTER TABLE orders
    ADD CONSTRAINT orders_attendant_id_fkey
    FOREIGN KEY (attendant_id) REFERENCES attendants(id);
```

- [ ] **Step 3: Rodar integration test do `IntegrationTest` minimal (até dar erro de código)**

Pular este step até as próximas tasks (Order ainda não foi refatorado). Mantemos a migration aplicada — Flyway só roda em `IntegrationTest.setup`.

- [ ] **Logical commit point:** "feat(storage): migrate users to attendants (drop password/role)"

---

## Task 8: `AttendantPostgresRepository` + Exposed table

**Files:**
- Create: `storage/src/main/kotlin/br/com/soat/attendant/Attendants.kt`
- Create: `storage/src/main/kotlin/br/com/soat/attendant/AttendantPostgresRepository.kt`

- [ ] **Step 1: Espelhar a tabela Exposed**

Olhar o existente em `storage/src/main/kotlin/br/com/soat/user/Users.kt` para seguir o padrão.

```
cat storage/src/main/kotlin/br/com/soat/user/Users.kt
```

Criar `storage/src/main/kotlin/br/com/soat/attendant/Attendants.kt` com o mesmo padrão mas sem `hashedPassword` e `role`. Exemplo (validar contra o `Users.kt` existente):

```kotlin
package br.com.soat.attendant

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object Attendants : UUIDTable("attendants", "id") {
    val name = varchar("name", 255)
    val document = varchar("document", 20).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val contact = varchar("contact", 20)
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version")
}
```

- [ ] **Step 2: Implementar repository**

`storage/src/main/kotlin/br/com/soat/attendant/AttendantPostgresRepository.kt`. Modelar a partir de `storage/src/main/kotlin/br/com/soat/user/UserPostgresRepository.kt`:

```
cat storage/src/main/kotlin/br/com/soat/user/UserPostgresRepository.kt
```

Conteúdo esperado (ajustar conforme o padrão real do `UserPostgresRepository`):

```kotlin
package br.com.soat.attendant

import br.com.soat.attendant.model.Attendant
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class AttendantPostgresRepository : AttendantRepository {

    override fun create(attendant: Attendant): Attendant = transaction {
        Attendants.insert {
            it[id] = attendant.id
            it[name] = attendant.name
            it[document] = attendant.document.value
            it[email] = attendant.email.value
            it[contact] = attendant.contact.value
            it[createdAt] = attendant.createdAt
            it[modifiedAt] = attendant.modifiedAt
            it[version] = attendant.version
        }
        attendant
    }

    override fun update(attendant: Attendant): Attendant = transaction {
        val now = LocalDateTime.now()
        Attendants.update({ Attendants.id eq attendant.id }) {
            it[name] = attendant.name
            it[document] = attendant.document.value
            it[email] = attendant.email.value
            it[contact] = attendant.contact.value
            it[modifiedAt] = now
            it[version] = attendant.version + 1
        }
        attendant.copy(modifiedAt = now, version = attendant.version + 1)
    }

    override fun delete(id: UUID) { transaction { Attendants.deleteWhere { Attendants.id eq id } } }

    override fun findById(id: UUID): Attendant? = transaction {
        Attendants.selectAll().where { Attendants.id eq id }.firstOrNull()?.toAttendant()
    }

    override fun findByDocument(document: Document): Attendant? = transaction {
        Attendants.selectAll().where { Attendants.document eq document.value }.firstOrNull()?.toAttendant()
    }

    override fun findAll(): List<Attendant> = transaction {
        Attendants.selectAll().map { it.toAttendant() }
    }

    private fun ResultRow.toAttendant() = Attendant(
        id = this[Attendants.id].value,
        name = this[Attendants.name],
        document = Document(this[Attendants.document]),
        email = Email(this[Attendants.email]),
        contact = PhoneNumber(this[Attendants.contact]),
        createdAt = this[Attendants.createdAt],
        modifiedAt = this[Attendants.modifiedAt],
        version = this[Attendants.version],
    )
}
```

Se houver divergência com o padrão do `UserPostgresRepository` (ex.: forma diferente de `selectAll().where`, datetime helper, etc.), seguir o padrão do existente — ele compila e é a verdade do projeto.

- [ ] **Step 3: Compilar**

Run:
```
./gradlew :storage:compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "feat(storage): add AttendantPostgresRepository + Attendants Exposed table"

---

## Task 9: `AttendantRoutes` + DTOs (CRUD admin-only)

**Files:**
- Create: `api/src/main/kotlin/br/com/soat/attendant/AttendantRoutes.kt`
- Create: `api/src/main/kotlin/br/com/soat/attendant/dto/CreateAttendantRequestDTO.kt`
- Create: `api/src/main/kotlin/br/com/soat/attendant/dto/UpdateAttendantRequestDTO.kt`
- Create: `api/src/main/kotlin/br/com/soat/attendant/dto/AttendantResponseDTO.kt`

- [ ] **Step 1: DTOs**

`api/src/main/kotlin/br/com/soat/attendant/dto/CreateAttendantRequestDTO.kt`:

```kotlin
package br.com.soat.attendant.dto

import br.com.soat.attendant.model.CreateAttendantRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class CreateAttendantRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {
    fun toModel() = CreateAttendantRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact),
    )
}
```

`api/src/main/kotlin/br/com/soat/attendant/dto/UpdateAttendantRequestDTO.kt`:

```kotlin
package br.com.soat.attendant.dto

import br.com.soat.attendant.model.UpdateAttendantRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class UpdateAttendantRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {
    fun toModel() = UpdateAttendantRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact),
    )
}
```

`api/src/main/kotlin/br/com/soat/attendant/dto/AttendantResponseDTO.kt`:

```kotlin
package br.com.soat.attendant.dto

import br.com.soat.attendant.model.Attendant
import java.time.LocalDateTime
import java.util.UUID

data class AttendantResponseDTO(
    val id: UUID,
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
) {
    companion object {
        fun from(attendant: Attendant) = AttendantResponseDTO(
            id = attendant.id,
            name = attendant.name,
            document = attendant.document.value,
            email = attendant.email.value,
            contact = attendant.contact.value,
            createdAt = attendant.createdAt,
            modifiedAt = attendant.modifiedAt,
        )
    }
}
```

- [ ] **Step 2: Routes (admin-only)**

`api/src/main/kotlin/br/com/soat/attendant/AttendantRoutes.kt`:

```kotlin
package br.com.soat.attendant

import br.com.soat.attendant.dto.AttendantResponseDTO
import br.com.soat.attendant.dto.CreateAttendantRequestDTO
import br.com.soat.attendant.dto.UpdateAttendantRequestDTO
import br.com.soat.shared.getUUIDPathParameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.attendantRoutes(koin: Koin) {
    val useCase = koin.inject<AttendantUseCase>().value

    routing {
        route("/v1") {
            authenticate("admin") {
                post("/attendants") {
                    val req = call.receive<CreateAttendantRequestDTO>()
                    val created = useCase.create(req.toModel())
                    call.respond(HttpStatusCode.Created, AttendantResponseDTO.from(created))
                }

                get("/attendants") {
                    val all = useCase.findAll()
                    call.respond(HttpStatusCode.OK, all.map { AttendantResponseDTO.from(it) })
                }

                get("/attendants/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    call.respond(HttpStatusCode.OK, AttendantResponseDTO.from(useCase.findById(id)))
                }

                put("/attendants/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val req = call.receive<UpdateAttendantRequestDTO>()
                    call.respond(HttpStatusCode.OK, AttendantResponseDTO.from(useCase.update(id, req.toModel())))
                }

                delete("/attendants/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    useCase.delete(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Compilar**

Run:
```
./gradlew :api:compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "feat(api): expose /v1/attendants CRUD under admin auth"

---

## Task 10: Refatorar `Order` para `attendantId: UUID` e `OrderUseCase` para usar `AttendantRepository`

**Files:**
- Modify: `domain/src/main/kotlin/br/com/soat/order/model/Order.kt` (campo attendant → attendantId)
- Modify: `domain/src/main/kotlin/br/com/soat/order/OrderUseCase.kt`
- Modify: `domain/src/main/kotlin/br/com/soat/order/model/request/CreateOrderRequest.kt` (drop attendantId)
- Modify: `api/src/main/kotlin/br/com/soat/order/OrderRoutes.kt` (extrai do principal)
- Modify: `api/src/main/kotlin/br/com/soat/order/dto/CreateOrderRequestDTO.kt`
- Modify: `api/src/main/kotlin/br/com/soat/order/dto/OrderResponseDTO.kt`
- Modify: `storage/src/main/kotlin/br/com/soat/order/OrderPostgresRepository.kt` (lê/grava attendantId direto)

- [ ] **Step 1: Inspecionar Order atual**

Run:
```
cat domain/src/main/kotlin/br/com/soat/order/model/Order.kt
```

Esperado: campo `attendant: User`. Anotar onde aparece (constructor, copy, etc.).

- [ ] **Step 2: Trocar `attendant: User` por `attendantId: UUID`**

Em `domain/src/main/kotlin/br/com/soat/order/model/Order.kt`:

- Remover import `br.com.soat.user.model.User`.
- Trocar `val attendant: User` por `val attendantId: UUID`.
- Em qualquer método/copy interno que use `attendant.id`, trocar por `attendantId`.

- [ ] **Step 3: `CreateOrderRequest` drop `attendantId` do request**

Em `domain/src/main/kotlin/br/com/soat/order/model/request/CreateOrderRequest.kt`, remover o campo `attendantId` (controller passa via parâmetro separado).

Ou, mais simples: manter o campo. Decisão: o **request body do HTTP** não tem `attendantId`, mas o `CreateOrderRequest` (modelo de domínio) continua tendo. O controller monta o request preenchendo `attendantId` do principal.

Manter `attendantId: UUID` em `CreateOrderRequest` (sem mudança).

- [ ] **Step 4: `OrderUseCase.create` troca `userRepository` por `attendantRepository`**

Em `domain/src/main/kotlin/br/com/soat/order/OrderUseCase.kt`:

Imports: remover `br.com.soat.user.*`. Adicionar:
```kotlin
import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.exception.AttendantNotFoundException
```

Construtor: trocar `private val userRepository: UserRepository,` por `private val attendantRepository: AttendantRepository,`.

Método `create`: trocar
```kotlin
        val attendant = userRepository.findById(request.attendantId)
            ?.takeIf { it.role == User.Role.ATTENDANT }
            ?: throw UserNotFoundException(request.attendantId)
```
por:
```kotlin
        val attendant = attendantRepository.findById(request.attendantId)
            ?: throw AttendantNotFoundException(request.attendantId)
```

E na criação do `Order(...)`, trocar `attendant = attendant,` por `attendantId = attendant.id,`.

- [ ] **Step 5: `CreateOrderRequestDTO` drop `attendantId` (HTTP)**

Em `api/src/main/kotlin/br/com/soat/order/dto/CreateOrderRequestDTO.kt`, remover o campo `attendantId` (o usuário não envia mais — vem do JWT).

A função `toModel(attendantId: UUID)` recebe o `attendantId` do controller:

```kotlin
fun toModel(attendantId: UUID) = CreateOrderRequest(
    customerId = customerId,
    vehicleId = vehicleId,
    description = description,
    attendantId = attendantId,
    servicesIds = servicesIds,
    extraSupplyRequirements = extraSuppliesRequests.map { SupplyRequirement(it.supplyId, it.quantity) },
)
```

(Ajustar exatamente conforme a forma atual do DTO.)

- [ ] **Step 6: `OrderRoutes` extrai `attendantId` do principal**

Em `api/src/main/kotlin/br/com/soat/order/OrderRoutes.kt`, no handler de `POST /v1/orders`, trocar:

```kotlin
val request = call.receive<CreateOrderRequestDTO>()
val created = orderUseCase.create(request.toModel())
```

por:

```kotlin
val principal = call.principal<JwtUserPrincipal>()!!
val request = call.receive<CreateOrderRequestDTO>()
val created = orderUseCase.create(request.toModel(attendantId = principal.userId))
```

Adicionar imports:
```kotlin
import br.com.soat.auth.JwtUserPrincipal
import io.ktor.server.auth.principal
```

- [ ] **Step 7: `OrderResponseDTO` reflete `attendantId`**

Em `api/src/main/kotlin/br/com/soat/order/dto/OrderResponseDTO.kt`, trocar qualquer referência a `order.attendant.id` por `order.attendantId`. Se existir campo nested (nome, email do attendant), substituir por só `attendantId: UUID`.

- [ ] **Step 8: Atualizar storage**

`storage/src/main/kotlin/br/com/soat/order/OrderPostgresRepository.kt`:
- Onde inserir / ler attendant, usar `order.attendantId` direto (era `order.attendant.id` antes).
- No `toOrder()` (row mapper), substituir o JOIN com `users` por leitura simples de `attendant_id` coluna. Anteriormente o code provavelmente faz `userRepository.findById(row[Orders.attendantId])` ou JOIN. Trocar por `attendantId = row[Orders.attendantId].value`.

Run para inspecionar:
```
grep -n "attendant\|Users\|User(" storage/src/main/kotlin/br/com/soat/order/OrderPostgresRepository.kt
```
Aplicar substituições conforme o que aparecer.

- [ ] **Step 9: Compilar**

Run:
```
./gradlew compileKotlin
```
Se quebrar em algum ponto não previsto (consumidores de `Order.attendant`), ajustar substituindo por `Order.attendantId`. O compilador é o guia.

Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "refactor(order): use attendantId UUID, drop User dependency in OrderUseCase"

---

## Task 11: Limpeza — remover módulo `user/`, `HashService`, `createDevAdmin`, bindings Koin

**Files (delete):**
- Delete recursivo: `domain/src/main/kotlin/br/com/soat/user/`
- Delete: `domain/src/main/kotlin/br/com/soat/security/HashService.kt` (se existir)
- Delete recursivo: `api/src/main/kotlin/br/com/soat/user/`
- Delete recursivo: `storage/src/main/kotlin/br/com/soat/user/`
- Delete recursivo: `main/src/test/kotlin/br/com/soat/user/`
- Modify: `main/src/main/kotlin/br/com/soat/Main.kt` (drop bindings + createDevAdmin)
- Modify: `api/src/main/kotlin/br/com/soat/config/RoutingConfiguration.kt`

- [ ] **Step 1: Confirmar nenhum import remanescente**

Run:
```
grep -rln "br.com.soat.user\.\|br.com.soat.security.Hash" \
    domain/src/main api/src/main storage/src/main main/src/main worker/src/main 2>/dev/null
```
Expected: vazio. Se aparecer algo, ajustar primeiro (a Task 10 deveria ter removido tudo do path principal; este step é confirmação).

- [ ] **Step 2: Apagar `user/` dos módulos**

```
rm -rf domain/src/main/kotlin/br/com/soat/user
rm -rf api/src/main/kotlin/br/com/soat/user
rm -rf storage/src/main/kotlin/br/com/soat/user
```

- [ ] **Step 3: Apagar `HashService` se existe e não tem mais consumidor**

```
find domain/src/main/kotlin -path "*security/HashService.kt"
```
Se retornar caminho, remover:
```
rm domain/src/main/kotlin/br/com/soat/security/HashService.kt
```
Se o diretório `security/` ficar vazio:
```
rmdir domain/src/main/kotlin/br/com/soat/security
```

Validar que ninguém usa:
```
grep -rln "HashService" domain api storage main worker | grep -v "/build/"
```
Expected: vazio.

- [ ] **Step 4: Apagar testes de user**

```
rm -rf main/src/test/kotlin/br/com/soat/user
```

- [ ] **Step 5: Atualizar `RoutingConfiguration`**

Em `api/src/main/kotlin/br/com/soat/config/RoutingConfiguration.kt`:

Remover import:
```kotlin
import br.com.soat.user.userRoutes
```
Adicionar:
```kotlin
import br.com.soat.attendant.attendantRoutes
```
Remover linha:
```kotlin
    userRoutes(koin)
```
Adicionar (mesmo lugar):
```kotlin
    attendantRoutes(koin)
```

- [ ] **Step 6: Limpar `Main.kt`**

Em `main/src/main/kotlin/br/com/soat/Main.kt`:

Remover imports:
```kotlin
import br.com.soat.security.HashService
import br.com.soat.user.UserPostgresRepository
import br.com.soat.user.UserRepository
import br.com.soat.user.UserUseCase
import br.com.soat.user.model.User
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import java.util.UUID.randomUUID
```

Adicionar:
```kotlin
import br.com.soat.attendant.AttendantPostgresRepository
import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.AttendantUseCase
```

Substituir em `applicationModule`:
```kotlin
    single<UserRepository> { UserPostgresRepository() }
```
por:
```kotlin
    single<AttendantRepository> { AttendantPostgresRepository() }
```

```kotlin
    single<HashService> { HashService(get()) }
    single<UserUseCase> { UserUseCase(get(), get()) }
```
por:
```kotlin
    single<AttendantUseCase> { AttendantUseCase(get()) }
```

Remover a função `createDevAdmin(...)` inteira e a chamada `createDevAdmin(koinApplication.koin)` dentro de `main()`. Não há substituto — provisionamento de admin agora é blackboxed no Lambda.

- [ ] **Step 7: Compilar e rodar testes unitários**

Run:
```
./gradlew test
```
Expected: PASS. Se houver test source ainda referenciando User (ex.: em outros módulos de teste), substituir por Attendant ou remover. Step 8 cobre integration tests.

- [ ] **Step 8: Ajustar `IntegrationTest` para Bearer JWT + Attendant**

Em `main/src/test/kotlin/br/com/soat/IntegrationTest.kt`:

Remover imports:
```kotlin
import br.com.soat.security.HashService
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.user.UserRepository
import br.com.soat.user.model.User
```

Substituir o método `adminHeaders()` por:

```kotlin
import java.util.Base64
import java.util.UUID

protected fun adminHeaders(userId: UUID = UUID.randomUUID()): Map<String, String> =
    mapOf("Authorization" to "Bearer ${fakeJwt(userId = userId, role = "ADMIN")}")

protected fun attendantHeaders(userId: UUID = UUID.randomUUID()): Map<String, String> =
    mapOf("Authorization" to "Bearer ${fakeJwt(userId = userId, role = "ATTENDANT")}")

private fun fakeJwt(userId: UUID, role: String): String {
    val header = b64u("""{"alg":"none","typ":"JWT"}""")
    val payload = b64u("""{"sub":"$userId","role":"$role","exp":9999999999}""")
    return "$header.$payload.test"
}

private fun b64u(s: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
```

- [ ] **Step 9: Atualizar test fixtures que criam User**

Run:
```
grep -rln "br.com.soat.user\|createUser\|UserFixtures" main/src/test 2>/dev/null
```

Em cada arquivo listado (provavelmente fixtures de outros módulos), substituir `createUser(...)` por algo equivalente em `attendant` (criar attendant via `AttendantRepository`) e gerar bearer token via `attendantHeaders(attendant.id)`.

Arquivos prováveis:
- `main/src/test/kotlin/br/com/soat/order/OrderLifecycleIntegrationTest.kt` (já tem `createUser` + `toAuthHeaders`)
- `main/src/test/kotlin/br/com/soat/order/OrderListingIntegrationTest.kt`
- `main/src/test/kotlin/br/com/soat/order/OrderMetricsIntegrationTest.kt`

Criar helper `main/src/test/kotlin/br/com/soat/attendant/AttendantFixtures.kt`:

```kotlin
package br.com.soat.attendant

import br.com.soat.IntegrationTest
import br.com.soat.attendant.model.Attendant
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import java.util.UUID

fun IntegrationTest.createAttendant(
    name: String = "Test Attendant",
    document: String = "11122233344",
    email: String = "attendant_${UUID.randomUUID()}@test.com",
    contact: String = "11999990000",
): Attendant {
    val repo = get<AttendantRepository>()
    return repo.create(
        Attendant(
            name = name,
            document = Document(document),
            email = Email(email),
            contact = PhoneNumber(contact),
        )
    )
}
```

Em `OrderLifecycleIntegrationTest`, substituir:
```kotlin
val attendant = createUser(role = User.Role.ATTENDANT)
...
val bearerToken = attendant.toAuthHeaders()
```
por:
```kotlin
val attendant = createAttendant()
val bearerToken = attendantHeaders(attendant.id)
```

E onde tem `attendantId = attendant.id` no `CreateOrderRequestDTO`, **remover** (campo não existe mais no DTO — controller pega do JWT).

E onde valida `createdOrder.attendant.id`, trocar por `createdOrder.attendantId`.

- [ ] **Step 10: Compilar + rodar integration tests**

Run:
```
./gradlew build integrationTest
```
Expected: PASS. Quebras esperadas (e como resolver):
- `unresolved reference: createUser` → remover import; substituir por `createAttendant`.
- `unresolved reference: toAuthHeaders` → substituir por `attendantHeaders(id)`.
- `OrderResponseDTO` sem campo `attendantId` → ver se está exposto; ajustar Task 10 step 7.

- [ ] **Logical commit point:** "refactor: drop User module, fixtures use Attendant + Bearer JWT"

---

## Task 12: `SendQuoteEmailCommand` + `SnsRelayCommandHandler`

Renomear/converter `QuoteEmailRequestedEvent` para Command. Substituir `SnsRelayEventHandler` por `SnsRelayCommandHandler`. Drop `SendQuoteToClientCommand` + handler.

**Files:**
- Create: `domain/src/main/kotlin/br/com/soat/order/command/SendQuoteEmailCommand.kt`
- Delete: `domain/src/main/kotlin/br/com/soat/order/event/QuoteEmailRequestedEvent.kt`
- Delete: `domain/src/main/kotlin/br/com/soat/order/command/SendQuoteToClientCommand.kt`
- Delete: `domain/src/main/kotlin/br/com/soat/order/command/handler/SendQuoteToClientCommandHandler.kt`
- Create: `worker/src/main/kotlin/br/com/soat/messaging/SnsRelayCommandHandler.kt`
- Delete: `worker/src/main/kotlin/br/com/soat/messaging/SnsRelayEventHandler.kt`
- Modify: `main/src/main/kotlin/br/com/soat/Main.kt` (Koin bindings)

- [ ] **Step 1: Criar `SendQuoteEmailCommand`**

`domain/src/main/kotlin/br/com/soat/order/command/SendQuoteEmailCommand.kt`:

```kotlin
package br.com.soat.order.command

import br.com.soat.command.model.Command
import br.com.soat.command.model.CommandStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class SendQuoteEmailCommand(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val status: CommandStatus = CommandStatus.PENDING,

    val orderId: UUID,
    val callbackToken: String,
    val customerEmail: String,
    val customerName: String,
    val totalAmount: BigDecimal,
    val services: List<Service>,
    val supplies: List<Supply>,
) : Command {

    data class Service(val name: String, val price: BigDecimal)
    data class Supply(val name: String, val quantity: Int, val unitPrice: BigDecimal)
}
```

- [ ] **Step 2: Apagar `QuoteEmailRequestedEvent`**

```
rm domain/src/main/kotlin/br/com/soat/order/event/QuoteEmailRequestedEvent.kt
```

- [ ] **Step 3: Apagar `SendQuoteToClientCommand` + handler**

```
rm domain/src/main/kotlin/br/com/soat/order/command/SendQuoteToClientCommand.kt
rm domain/src/main/kotlin/br/com/soat/order/command/handler/SendQuoteToClientCommandHandler.kt
```

- [ ] **Step 4: Criar `SnsRelayCommandHandler`**

`worker/src/main/kotlin/br/com/soat/messaging/SnsRelayCommandHandler.kt`:

```kotlin
package br.com.soat.messaging

import br.com.soat.command.handler.CommandHandler
import br.com.soat.command.model.Command
import br.com.soat.order.command.SendQuoteEmailCommand
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class SnsRelayCommandHandler(
    private val sns: SnsClient,
    private val mapper: ObjectMapper,
) : CommandHandler {

    private val logger = LoggerFactory.getLogger(SnsRelayCommandHandler::class.java)

    override val commandType: KClass<out Command> = SendQuoteEmailCommand::class

    override fun handle(command: Command) {
        if (command !is SendQuoteEmailCommand) return

        val payload = mapper.writeValueAsString(command)
        sns.publish(
            payload = payload,
            eventType = "SendQuoteEmailCommand",
            messageId = command.id.toString(),
        )
        logger.info("Published command ${command::class.simpleName}/${command.id} to SNS")
    }
}
```

- [ ] **Step 5: Apagar `SnsRelayEventHandler`**

```
rm worker/src/main/kotlin/br/com/soat/messaging/SnsRelayEventHandler.kt
```

- [ ] **Step 6: Atualizar Koin no `Main.kt`**

Em `main/src/main/kotlin/br/com/soat/Main.kt`:

Remover imports:
```kotlin
import br.com.soat.messaging.SnsRelayEventHandler
import br.com.soat.order.command.handler.SendQuoteToClientCommandHandler
```

Adicionar:
```kotlin
import br.com.soat.messaging.SnsRelayCommandHandler
```

Remover bindings:
```kotlin
    single { SendQuoteToClientCommandHandler(get()) } bind CommandHandler::class
    single { SnsRelayEventHandler(get(), get()) } bind EventHandler::class
```

Adicionar:
```kotlin
    single { SnsRelayCommandHandler(get(), get()) } bind CommandHandler::class
```

- [ ] **Step 7: Compilar (Task 13 conserta o resto)**

Run:
```
./gradlew :domain:compileKotlin :worker:compileKotlin
```

Esperar erros em `OrderListenerUseCase` (referencia `QuoteEmailRequestedEvent`). É esperado — Task 13 conserta.

- [ ] **Logical commit point:** "feat(domain): SendQuoteEmailCommand replaces QuoteEmailRequestedEvent; SnsRelayCommandHandler replaces event handler"

---

## Task 13: Fundir `OrderListenerUseCase.sendQuoteToApproval` (uma TX só)

`OrderListenerUseCase` hoje tem dois métodos (`sendQuoteToApproval` + `sendQuoteApprovalEmail`) por causa do intermediário `SendQuoteToClientCommand`. Funde tudo em um método que salva `SendQuoteEmailCommand` direto no outbox de commands.

**Files:**
- Modify: `domain/src/main/kotlin/br/com/soat/order/OrderListenerUseCase.kt`

- [ ] **Step 1: Reescrever o use case**

Substituir o conteúdo de `domain/src/main/kotlin/br/com/soat/order/OrderListenerUseCase.kt` por:

```kotlin
package br.com.soat.order

import br.com.soat.command.CommandPublisher
import br.com.soat.command.repository.CommandRepository
import br.com.soat.order.command.SendQuoteEmailCommand
import br.com.soat.order.exception.OrderNotFoundException
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderApprovalToken
import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.repository.SupplyRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class OrderListenerUseCase(
    private val supplyRepository: SupplyRepository,
    private val orderRepository: OrderRepository,
    private val commandRepository: CommandRepository,
    private val commandPublisher: CommandPublisher,
    private val orderApprovalTokenRepository: OrderApprovalTokenRepository,
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository,
    private val tx: RepositoryTransactionHandler,
) {

    fun sendQuoteToApproval(orderId: UUID) {
        val order = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)

        val requiredSupplies = order.getSupplyRequirements()
        val supplies = supplyRepository.findAllByIds(requiredSupplies.map { it.supplyId })

        val command = tx.inTransaction {
            orderRepository.update(order.waitingApproval())

            val approvalToken = orderApprovalTokenRepository.save(
                OrderApprovalToken(
                    orderId = orderId,
                    expiresAt = LocalDateTime.now().plusDays(5),
                )
            )

            val totalServices = order.services.sumOf { it.price }
            val totalSupplies = supplies.sumOf {
                val qty = requiredSupplies.single { req -> req.supplyId == it.id }.quantity
                it.price * BigDecimal(qty)
            }

            commandRepository.save(
                SendQuoteEmailCommand(
                    orderId = orderId,
                    callbackToken = approvalToken.id.toString(),
                    customerEmail = order.customer.email.value,
                    customerName = order.customer.name,
                    totalAmount = totalServices + totalSupplies,
                    services = order.services.map {
                        SendQuoteEmailCommand.Service(name = it.name, price = it.price)
                    },
                    supplies = supplies.map {
                        SendQuoteEmailCommand.Supply(
                            name = it.name,
                            quantity = requiredSupplies.single { req -> req.supplyId == it.id }.quantity,
                            unitPrice = it.price,
                        )
                    },
                )
            )
        }

        commandPublisher.publish(command)
    }

    fun registerExecutionTimeMetric(orderId: UUID, status: Order.Status) {
        when (status) {
            Order.Status.IN_PROGRESS -> {
                orderExecutionMetricRepository.create(
                    OrderExecutionMetric(
                        orderId = orderId,
                        inProgressAt = LocalDateTime.now(),
                    )
                )
            }
            Order.Status.COMPLETED -> {
                val existingMetric = orderExecutionMetricRepository.findByOrderId(orderId)
                if (existingMetric != null) {
                    orderExecutionMetricRepository.update(
                        existingMetric.copy(completedAt = LocalDateTime.now())
                    )
                }
            }
            else -> {}
        }
    }
}
```

- [ ] **Step 2: Atualizar Koin (`OrderListenerUseCase` agora tem menos deps)**

Em `main/src/main/kotlin/br/com/soat/Main.kt`:

Trocar:
```kotlin
    single<OrderListenerUseCase> { OrderListenerUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
```
por (7 dependências em vez de 9 — confirma com a ordem do construtor):
```kotlin
    single<OrderListenerUseCase> { OrderListenerUseCase(get(), get(), get(), get(), get(), get(), get()) }
```

A ordem do construtor (do código novo): supplyRepository, orderRepository, commandRepository, commandPublisher, orderApprovalTokenRepository, orderExecutionMetricRepository, tx → 7 deps.

- [ ] **Step 3: Atualizar handlers que chamavam `sendQuoteApprovalEmail`**

Procurar:
```
grep -rn "sendQuoteApprovalEmail" .
```
Expected: nenhum match (já apagamos `SendQuoteToClientCommandHandler` na Task 12, que era o único caller).

Procurar quem chama `sendQuoteToApproval`:
```
grep -rn "sendQuoteToApproval" domain api main worker | grep -v build
```
Esperado: `OrderInProgressEventHandler` ou `OrderDiagnoseFinishedEventHandler` (ou ambos) — verificar arquivos e confirmar que chamam `useCase.sendQuoteToApproval(orderId)`. Sem mudança neles.

- [ ] **Step 4: Compilar**

Run:
```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "refactor(order): fuse OrderListenerUseCase.sendQuoteToApproval into one TX with SendQuoteEmailCommand"

---

## Task 14: Drop flag `external` do `DomainEvent` + branch do `DefaultEventPublisher`

**Files:**
- Modify: `domain/src/main/kotlin/br/com/soat/event/model/DomainEvent.kt`
- Modify: `worker/src/main/kotlin/br/com/soat/publisher/DefaultEventPublisher.kt`

- [ ] **Step 1: Limpar `DomainEvent`**

Em `domain/src/main/kotlin/br/com/soat/event/model/DomainEvent.kt`:

Substituir por:

```kotlin
package br.com.soat.event.model

import java.time.LocalDateTime
import java.util.UUID

interface DomainEvent {
    val id: UUID
    val createdAt: LocalDateTime
    val modifiedAt: LocalDateTime
    val version: Int
}
```

- [ ] **Step 2: Limpar `DefaultEventPublisher`**

Em `worker/src/main/kotlin/br/com/soat/publisher/DefaultEventPublisher.kt`, remover o bloco:

```kotlin
        if (event.external) {
            logger.info("External event ${event::class.simpleName}/${event.id} queued for scheduler relay")
            return
        }
```

O `publish` final fica:

```kotlin
    override fun publish(event: DomainEvent) {
        try {
            logger.info("Publishing event ${event::class.simpleName} with id ${event.id}")
            CoroutineScope(dispatcher).launch {
                eventBus.publish(event)
            }
        } catch (e: Exception) {
            logger.warn("Failed to publish event ${event.id} immediately. Will be processed by scheduler.", e)
        }
    }
```

- [ ] **Step 3: Confirmar nenhum lugar lendo `event.external`**

Run:
```
grep -rn "external" domain/src/main worker/src/main | grep -i "event\|command"
```
Expected: vazio (ou apenas matches em CHANGELOG, docs, etc.).

- [ ] **Step 4: Compilar**

Run:
```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Logical commit point:** "refactor(event): drop external flag and bypass branch in DefaultEventPublisher"

---

## Task 15: `SnsClient` aceita `endpointOverride` e credenciais; LocalStack setup no `IntegrationTest`

**Files:**
- Modify: `worker/src/main/kotlin/br/com/soat/messaging/SnsClient.kt`
- Modify: `main/src/main/kotlin/br/com/soat/Main.kt` (Koin do SnsClient lê config opcional)
- Modify: `main/build.gradle.kts` (testImplementation localstack + sqs)
- Modify: `gradle/libs.versions.toml` (adicionar localstack + sqs)
- Modify: `main/src/test/kotlin/br/com/soat/IntegrationTest.kt`
- Modify: `main/src/test/resources/application-test.yaml`

- [ ] **Step 1: Adicionar libs em `libs.versions.toml`**

Em `gradle/libs.versions.toml`, na seção `[libraries]`, adicionar (depois de `aws-sdk-kotlin-sns`):

```toml
aws-sdk-kotlin-sqs = { module = "aws.sdk.kotlin:sqs", version.ref = "aws-sdk-kotlin" }

testcontainers-localstack = { module = "org.testcontainers:localstack", version.ref = "testcontainers" }
```

- [ ] **Step 2: Adicionar dependências em `main/build.gradle.kts`**

Adicionar no bloco `dependencies`:

```kotlin
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.aws.sdk.kotlin.sqs)
    testImplementation(libs.aws.sdk.kotlin.sns)
```

(o `aws-sdk-kotlin-sns` já é runtime via `worker`, mas pode aparecer em fixture de teste — incluir em `testImplementation` evita warning).

- [ ] **Step 3: `SnsClient` aceita endpoint + credenciais opcionais**

Em `worker/src/main/kotlin/br/com/soat/messaging/SnsClient.kt`, trocar pelo conteúdo:

```kotlin
package br.com.soat.messaging

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sns.model.MessageAttributeValue
import aws.sdk.kotlin.services.sns.model.PublishRequest
import aws.smithy.kotlin.runtime.net.url.Url
import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import kotlinx.coroutines.runBlocking

class SnsClient(
    private val topicArn: String,
    region: String = "us-east-1",
    endpointOverride: String? = null,
    accessKeyId: String? = null,
    secretAccessKey: String? = null,
) : AutoCloseable {

    private val client: AwsSnsClient = runBlocking {
        AwsSnsClient {
            this.region = region
            if (endpointOverride != null) {
                this.endpointUrl = Url.parse(endpointOverride)
            }
            if (accessKeyId != null && secretAccessKey != null) {
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = accessKeyId
                    this.secretAccessKey = secretAccessKey
                }
            }
        }
    }

    fun publish(payload: String, eventType: String, messageId: String) {
        runBlocking {
            client.publish(
                PublishRequest {
                    topicArn = this@SnsClient.topicArn
                    message = payload
                    messageAttributes = mapOf(
                        "event_type" to MessageAttributeValue {
                            dataType = "String"
                            stringValue = eventType
                        },
                        "message_id" to MessageAttributeValue {
                            dataType = "String"
                            stringValue = messageId
                        },
                    )
                },
            )
        }
    }

    override fun close() {
        client.close()
    }
}
```

Notas:
- `aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider` e `aws.smithy.kotlin.runtime.net.url.Url` são os caminhos esperados na versão 1.5.x; se quebrar import, ajustar conforme `aws-sdk-kotlin` docs do contexto7 (rodar `mcp__plugin_context7_context7__resolve-library-id` com query "aws sdk kotlin").

- [ ] **Step 4: Koin lê configurações opcionais**

Em `main/src/main/kotlin/br/com/soat/Main.kt`, trocar:

```kotlin
    single { SnsClient(topicArn = get<Config>().getString("sns.topic.arn")) }
```

por:

```kotlin
    single {
        val cfg = get<Config>()
        SnsClient(
            topicArn = cfg.getString("sns.topic.arn"),
            region = cfg.getStringOrNull("aws.region") ?: "us-east-1",
            endpointOverride = cfg.getStringOrNull("aws.endpoint"),
            accessKeyId = cfg.getStringOrNull("aws.accessKeyId"),
            secretAccessKey = cfg.getStringOrNull("aws.secretAccessKey"),
        )
    }
```

Se `getStringOrNull` não existir na classe `Config`, adicionar:

Procurar:
```
grep -n "fun getString" main/src/main/kotlin/br/com/soat/config/Config.kt
```
Se a classe `Config` (ou ConfigFactory) só tem `getString` que lança, adicionar:

```kotlin
fun getStringOrNull(key: String): String? = try { getString(key) } catch (_: Exception) { null }
```

- [ ] **Step 5: Atualizar `application-test.yaml`**

Em `main/src/test/resources/application-test.yaml`, adicionar (ou substituir as chaves SNS/AWS):

```yaml
sns:
  topic:
    arn: ${SNS_TOPIC_ARN}
aws:
  region: us-east-1
  endpoint: ${AWS_ENDPOINT}
  accessKeyId: test
  secretAccessKey: test
```

(Os placeholders `${SNS_TOPIC_ARN}` e `${AWS_ENDPOINT}` serão sobrescritos no `IntegrationTest.setup()` via `config.put(...)`.)

- [ ] **Step 6: Atualizar `IntegrationTest.kt`**

Substituir o conteúdo de `main/src/test/kotlin/br/com/soat/IntegrationTest.kt`. Estrutura nova:

```kotlin
package br.com.soat

import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import aws.sdk.kotlin.services.sns.model.CreateTopicRequest
import aws.sdk.kotlin.services.sns.model.SubscribeRequest
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueAttributesRequest
import aws.sdk.kotlin.services.sqs.model.PurgeQueueRequest
import aws.sdk.kotlin.services.sqs.model.QueueAttributeName
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.smithy.kotlin.runtime.net.url.Url
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.consumer.CommandConsumerWorker
import br.com.soat.consumer.EventConsumerWorker
import br.com.soat.scheduler.ScheduledTaskRunner
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.ServerSocket
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1").withReuse(true)!!
    protected val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
        .withServices(SNS, SQS)
        .withReuse(true)

    lateinit var koinApplication: KoinApplication
    lateinit var server: KtorHttpServer
    lateinit var http: IntegrationTestHttpClient
    lateinit var sqsClient: SqsClient
    lateinit var queueUrl: String

    val httpClient: IntegrationTestHttpClient
        get() = http

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            exec("""
                DO ${'$'}${'$'} DECLARE
                    r RECORD;
                BEGIN
                    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                    END LOOP;
                END ${'$'}${'$'};
            """.trimIndent())
        }
        runBlocking {
            sqsClient.purgeQueue(PurgeQueueRequest { queueUrl = this@IntegrationTest.queueUrl })
        }
    }

    @BeforeAll
    open fun setup() {
        postgresContainer.start()
        localstack.start()

        val endpoint = localstack.getEndpointOverride(SNS).toString()
        val (topicArn, qUrl) = createSnsQueueWithSubscription(endpoint)
        queueUrl = qUrl

        val config = Config.fromClasspath("application-test.yaml").apply {
            put("database.url", postgresContainer.jdbcUrl)
            put("database.username", postgresContainer.username)
            put("database.password", postgresContainer.password)
            put("database.driverClassName", postgresContainer.driverClassName)
            put("server.port", serverPort)
            put("sns.topic.arn", topicArn)
            put("aws.endpoint", endpoint)
            put("aws.region", localstack.region)
            put("aws.accessKeyId", localstack.accessKey)
            put("aws.secretAccessKey", localstack.secretKey)
        }

        val dataSource = connectToDatabase(config)

        koinApplication = startKoin { modules(applicationModule, testModule(config)) }

        http = IntegrationTestHttpClient(serverPort)

        sqsClient = runBlocking {
            SqsClient {
                this.region = localstack.region
                this.endpointUrl = Url.parse(endpoint)
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = localstack.accessKey
                    this.secretAccessKey = localstack.secretKey
                }
            }
        }

        get<EventConsumerWorker>().start()
        get<CommandConsumerWorker>().start()

        get<ScheduledTaskRunner>().start(dataSource)

        server = KtorHttpServer(
            koin = koinApplication.koin,
            port = serverPort,
            wait = false
        )
        server.start()
    }

    private fun createSnsQueueWithSubscription(endpoint: String): Pair<String, String> = runBlocking {
        val sns = AwsSnsClient {
            this.region = localstack.region
            this.endpointUrl = Url.parse(endpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localstack.accessKey
                this.secretAccessKey = localstack.secretKey
            }
        }
        val sqs = SqsClient {
            this.region = localstack.region
            this.endpointUrl = Url.parse(endpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localstack.accessKey
                this.secretAccessKey = localstack.secretKey
            }
        }

        val topicArn = sns.createTopic(CreateTopicRequest { name = "auto-repair-shop-events-test" }).topicArn!!
        val createdQueue = sqs.createQueue(CreateQueueRequest { queueName = "email-queue-test" })
        val queueUrl = createdQueue.queueUrl!!
        val queueArn = sqs.getQueueAttributes(GetQueueAttributesRequest {
            this.queueUrl = queueUrl
            attributeNames = listOf(QueueAttributeName.QueueArn)
        }).attributes!![QueueAttributeName.QueueArn]!!

        sns.subscribe(SubscribeRequest {
            this.topicArn = topicArn
            protocol = "sqs"
            endpoint = queueArn
            attributes = mapOf(
                "RawMessageDelivery" to "true",
                "FilterPolicy" to """{"event_type":["SendQuoteEmailCommand"]}""",
            )
        })

        sns.close()
        sqs.close()
        topicArn to queueUrl
    }

    @AfterAll
    fun tearDown() {
        server.stop()
        postgresContainer.stop()
        get<ScheduledTaskRunner>().stop()
        sqsClient.close()
        stopKoin()
    }

    inline fun <reified T> get(): T = koinApplication.koin.get()

    protected fun adminHeaders(userId: UUID = UUID.randomUUID()): Map<String, String> =
        mapOf("Authorization" to "Bearer ${fakeJwt(userId = userId, role = "ADMIN")}")

    protected fun attendantHeaders(userId: UUID = UUID.randomUUID()): Map<String, String> =
        mapOf("Authorization" to "Bearer ${fakeJwt(userId = userId, role = "ATTENDANT")}")

    private fun fakeJwt(userId: UUID, role: String): String {
        val header = b64u("""{"alg":"none","typ":"JWT"}""")
        val payload = b64u("""{"sub":"$userId","role":"$role","exp":9999999999}""")
        return "$header.$payload.test"
    }

    private fun b64u(s: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    protected fun waitForSnsMessage(eventType: String, timeoutSeconds: Long = 5): JsonNode {
        val mapper = get<ObjectMapper>()
        val deadline = Instant.now().plusSeconds(timeoutSeconds)
        while (Instant.now().isBefore(deadline)) {
            val msg = runBlocking {
                sqsClient.receiveMessage(ReceiveMessageRequest {
                    queueUrl = this@IntegrationTest.queueUrl
                    maxNumberOfMessages = 1
                    waitTimeSeconds = 1
                }).messages?.firstOrNull()
            }
            if (msg != null) {
                val payload = mapper.readTree(msg.body)
                runBlocking {
                    sqsClient.deleteMessage(DeleteMessageRequest {
                        queueUrl = this@IntegrationTest.queueUrl
                        receiptHandle = msg.receiptHandle
                    })
                }
                return payload
            }
        }
        fail("Timeout waiting for SNS message with event_type=$eventType")
    }

    private fun testModule(config: Config) = module {
        single<Config> { config }
    }
}
```

Notas:
- O `single<SnsClient> { mockk(relaxed = true) }` foi **removido** — agora o Koin de produção monta o `SnsClient` real com endpoint LocalStack via config.
- Mantém `withReuse(true)` no LocalStack pra acelerar reruns locais.
- A versão `localstack/localstack:3.7` está estável; bumpar conforme necessário.

- [ ] **Step 7: Build + smoke test**

Run:
```
./gradlew build
```
Expected: BUILD SUCCESSFUL.

```
./gradlew :main:integrationTest --tests "br.com.soat.customer.*"
```

(Roda algum integration test não-relacionado a SNS pra validar setup.) Expected: PASS. Se LocalStack não baixar imagem, garantir conectividade Docker.

- [ ] **Logical commit point:** "test: replace SnsClient mock with real LocalStack SNS+SQS in IntegrationTest"

---

## Task 16: `OrderLifecycleIntegrationTest` assert payload no SQS

**Files:**
- Modify: `main/src/test/kotlin/br/com/soat/order/OrderLifecycleIntegrationTest.kt`

- [ ] **Step 1: Reescrever as asserções de SNS no lifecycle test**

Abrir `main/src/test/kotlin/br/com/soat/order/OrderLifecycleIntegrationTest.kt`. As asserções atuais que precisam mudar:

```kotlin
// assert QuoteEmailRequestedEvent was written to outbox
val quoteEvent = eventRepository
    .findAllBy(QuoteEmailRequestedEvent::class.qualifiedName!!, br.com.soat.event.model.EventStatus.PROCESSED, 10)
    .filterIsInstance<QuoteEmailRequestedEvent>()
    .single { it.orderId == orderWaitingApproval!!.id }

assertEquals(approvalToken!!.id.toString(), quoteEvent.callbackToken)
assertEquals(customer.name, quoteEvent.customerName)
assertEquals(customer.email.value, quoteEvent.customerEmail)
assertEquals(orderWaitingApproval!!.services.map { it.name }, quoteEvent.services.map { it.name })
assertEquals(
    listOf(QuoteEmailRequestedEvent.Supply(supply.name, 8, supply.price)),
    quoteEvent.supplies,
)

// assert SnsRelayEventHandler invoked SnsClient with the event payload
verify { snsClient.publish(any(), eq("QuoteEmailRequestedEvent"), eq(quoteEvent.id.toString())) }
```

Substituir por:

```kotlin
// assert SendQuoteEmailCommand was published to SNS (received via LocalStack SQS subscriber)
val msg = waitForSnsMessage("SendQuoteEmailCommand")

assertEquals(orderWaitingApproval!!.id.toString(), msg["orderId"].asText())
assertEquals(approvalToken!!.id.toString(), msg["callbackToken"].asText())
assertEquals(customer.name, msg["customerName"].asText())
assertEquals(customer.email.value, msg["customerEmail"].asText())
assertEquals(
    orderWaitingApproval!!.services.map { it.name },
    msg["services"].map { it["name"].asText() },
)
assertEquals(1, msg["supplies"].size())
assertEquals(supply.name, msg["supplies"][0]["name"].asText())
assertEquals(8, msg["supplies"][0]["quantity"].asInt())
```

E remover imports não usados (`QuoteEmailRequestedEvent`, `EventStatus`, `EventRepository`, `eventRepository`, `verify`, `SnsClient`, `snsClient` se não usados em outro lugar). Limpar conforme o compilador apontar.

- [ ] **Step 2: Atualizar fixture do attendant + DTO sem `attendantId`**

No mesmo arquivo, ajustar (já cobertos parcialmente na Task 11):

```kotlin
val attendant = createUser(role = User.Role.ATTENDANT)
...
val bearerToken = attendant.toAuthHeaders()
```
→
```kotlin
val attendant = createAttendant()
val bearerToken = attendantHeaders(attendant.id)
```

E no `CreateOrderRequestDTO(...)` remover `attendantId = attendant.id,`.

E no assert:
```kotlin
assertEquals(attendant.id, createdOrder.attendant.id)
```
→
```kotlin
assertEquals(attendant.id, createdOrder.attendantId)
```

- [ ] **Step 3: Rodar o lifecycle test**

Run:
```
./gradlew :main:integrationTest --tests "br.com.soat.order.OrderLifecycleIntegrationTest"
```
Expected: PASS.

Se quebrar com timeout no `waitForSnsMessage`:
- Aumentar `timeoutSeconds = 10`.
- Confirmar que `SnsRelayCommandHandler` está no Koin (`bind CommandHandler::class`).
- Confirmar que `CommandConsumerWorker.start()` foi chamado (já é, no setup).
- Logs do app: `kubectl logs` não aplica aqui — capturar stdout do teste; procurar "Published command SendQuoteEmailCommand".

- [ ] **Step 4: Rodar todos os integration tests**

Run:
```
./gradlew integrationTest
```
Expected: PASS.

- [ ] **Logical commit point:** "test(order): assert SendQuoteEmailCommand payload on SQS in lifecycle test"

---

## Task 17: Atualizar README + OpenAPI

**Files:**
- Modify: `README.md`
- Modify: `api/src/main/resources/openapi/documentation.yaml`

- [ ] **Step 1: README**

Abrir `README.md`. Procurar referências a:
- `X-User-Id` / `X-User-Role` → trocar por exemplo de `Authorization: Bearer <jwt>`.
- `/v1/users/` → trocar por `/v1/attendants/` (e remover qualquer menção a CRUD de admin no app — agora é blackbox no Lambda).
- `User` como entidade do app → reescrever como `Attendant`.
- `HashService` ou senha hasheada → remover (Lambda owns).

Run:
```
grep -n "X-User-Id\|X-User-Role\|/v1/users\|HashService\|hashedPassword" README.md
```
Trocar cada ocorrência.

- [ ] **Step 2: OpenAPI**

Abrir `api/src/main/resources/openapi/documentation.yaml`.

Mudanças:
- Remover paths sob `/v1/users` inteiramente.
- Adicionar paths sob `/v1/attendants` (POST, GET list, GET by id, PUT, DELETE).
- Substituir `securitySchemes` definindo:
  ```yaml
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  ```
- Aplicar `security: [{ bearerAuth: [] }]` nos endpoints autenticados.
- Em `CreateOrderRequest` schema: remover propriedade `attendantId`.
- Em `OrderResponse` schema: substituir nested user por `attendantId: uuid`.

Como o arquivo é grande, fazer edits cirúrgicos com `grep -n` pra localizar seções.

- [ ] **Step 3: Validar OpenAPI**

Run:
```
./gradlew :api:test
```
Expected: PASS (Ktor Swagger valida o YAML no startup do servidor; passa pelos integration tests).

- [ ] **Step 4: Smoke test do Swagger UI**

(Opcional, manual) Subir app local + `curl http://localhost:8080/swagger` e abrir no browser. Confirmar que `/v1/attendants` aparece e `/v1/users` não.

- [ ] **Logical commit point:** "docs: README + OpenAPI reflect Bearer JWT and Attendant routes"

---

## Task 18: Atualizar memory `feedback_auth_delegated.md`

O memory atual diz "app só confia em X-User-Id/X-User-Role headers" — desatualizado.

**Files:**
- Modify: `/home/ivanzao/.claude/projects/-home-ivanzao-dev-repository-auto-repair-shop/memory/feedback_auth_delegated.md`

- [ ] **Step 1: Ler memory atual**

```
cat /home/ivanzao/.claude/projects/-home-ivanzao-dev-repository-auto-repair-shop/memory/feedback_auth_delegated.md
```

- [ ] **Step 2: Reescrever**

Substituir o body por:

```markdown
---
name: feedback-auth-delegated
description: Auth está delegada para a Lambda Authorizer; o app recebe Authorization Bearer JWT e só decoda os claims (sem revalidar assinatura)
metadata:
  type: feedback
---

App só conhece JWT via `Authorization: Bearer <jwt>`. Lambda Authorizer (no API Gateway) valida assinatura, exp e role; app apenas decoda o payload pra extrair `sub` (userId) e `role`. Sem revalidação de assinatura no app.

**Why:** Lambda Authorizer já é a única fronteira de autenticação; o NLB do app é privado em VPC, alcançável só via VPC Link do API Gateway. Replicar a validação no app duplicaria responsabilidade e exigiria distribuir o segredo HMAC pro pod.

**How to apply:** Sempre que tocar em auth no app, lembrar que (a) não existe `/auth/login` no app (vive em Lambda); (b) `JwtBearerAuthenticationProvider` lê o Bearer e popula `JwtUserPrincipal(userId, role)`; (c) o app **não tem** mais o conceito de User/admin — só `Attendant` (entidade de domínio sem credenciais). Atualizou pra Bearer JWT em 2026-05-23 (antes era `X-User-Id`/`X-User-Role` injetado por header).
```

- [ ] **Step 3: Confirmar `MEMORY.md` segue apontando pra esse memory (sem mudança no descritor)**

Não precisa editar `MEMORY.md` — o ponteiro continua válido.

- [ ] **Step 4: Atualizar descrição em `MEMORY.md`**

Em `/home/ivanzao/.claude/projects/-home-ivanzao-dev-repository-auto-repair-shop/memory/MEMORY.md`, trocar:

```
- [feedback_auth_delegated.md](feedback_auth_delegated.md) — Auth/login/status delegado pra Lambda Authorizer; app só confia em X-User-Id/X-User-Role headers
```
por:
```
- [feedback_auth_delegated.md](feedback_auth_delegated.md) — Auth delegada pra Lambda Authorizer; app recebe Authorization Bearer JWT e decoda claims sem revalidar assinatura
```

- [ ] **Logical commit point (memory-only):** sem ação git — memory vive fora do repo do projeto.

---

## Task 19: Build final completo

- [ ] **Step 1: Build e testes**

Run:
```
./gradlew clean build integrationTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirmar domain não conhece infraestrutura**

Run:
```
grep -r "io.micrometer\|aws.sdk\|io.ktor\|org.testcontainers" domain/src/main 2>/dev/null || echo OK
```
Expected: `OK`.

- [ ] **Step 3: Confirmar nenhum vestígio de User/QuoteEmailRequestedEvent/HeaderAuth**

Run:
```
grep -rln "QuoteEmailRequestedEvent\|HeaderAuthenticationProvider\|SendQuoteToClientCommand\|HashService\|UserUseCase\|UserRepository\|class User(" \
    domain api storage worker metric main 2>/dev/null | grep -v "/build/" || echo OK
```
Expected: `OK`.

- [ ] **Step 4: Confirmar criterios de aceitação do spec**

Repassar a Sec 11 do spec (`docs/superpowers/specs/2026-05-23-sns-command-refactor-design.md`). Cada checkbox deve estar atendido pelas tasks acima.

---

## Self-review notes

Cobertura do spec → tasks:

| Spec section | Tasks |
|---|---|
| 5 — SendQuoteEmailCommand + relay | 12, 13, 14 |
| 6 — Attendant + JWT Bearer + drop User | 5, 6, 7, 8, 9, 10, 11 |
| 7 — MetricsPort | 2, 3, 4 |
| 8 — Tag latest | 1 |
| 9 — LocalStack | 15, 16 |
| README + OpenAPI | 17 |
| Memory update | 18 |
| Final validation | 19 |

Sem placeholders. Sem `--no-verify` em git (sem comandos git). Tipos e assinaturas consistentes entre tasks (verificado: `SendQuoteEmailCommand` com mesmos campos em Task 12/13/16; `JwtUserPrincipal` em Task 5/10; `MetricsPort` em Task 2/3/4; `Attendant` em Task 6/8/9/10/11).
