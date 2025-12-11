package br.com.soat

import br.com.soat.auth.authenticationRoutes
import br.com.soat.customer.customerRoutes
import br.com.soat.order.orderRoutes
import br.com.soat.security.configureAuthentication
import br.com.soat.supply.supplyRoutes
import br.com.soat.user.userRoutes
import br.com.soat.vehicle.vehicleRoutes
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.core.Koin

class KtorHttpServer(
    private val koin: Koin,
    private val port: Int = 8080,
    private val gracePeriodMillis: Long = 1000,
    private val timeoutMillis: Long = 2000,
    private val wait: Boolean = true,
) : HttpServer {

    private var server: EmbeddedServer<*, *>? = null

    init {
        server = embeddedServer(Netty, port = port) {
            configureAuthentication(koin)
            configureRouting(koin)
            configureSerialization()
            configureLogging()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            server?.stop(1000, 2000)
        })
    }


    private fun Application.configureRouting(koin: Koin) {
        routing {
            get("/health") {
                call.respondText("OK")
            }
        }

        userRoutes(koin)
        supplyRoutes(koin)
        vehicleRoutes(koin)
        customerRoutes(koin)
        orderRoutes(koin)
        authenticationRoutes(koin)
    }

    private fun Application.configureLogging() {
        install(CallLogging)
    }

    private fun Application.configureSerialization() {
        install(ContentNegotiation) {
            jackson {
                findAndRegisterModules()
//                registerKotlinModule()
//                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    override fun start() {
        server?.start(wait = wait)
            ?: throw IllegalStateException("NettyApplicationEngine is not initialized")
    }

    override fun stop() {
        server?.stop(gracePeriodMillis, timeoutMillis)
        server = null
    }
}