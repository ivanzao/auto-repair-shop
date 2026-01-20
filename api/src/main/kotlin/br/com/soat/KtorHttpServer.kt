package br.com.soat

import br.com.soat.config.configureAuthentication
import br.com.soat.config.configureErrorHandling
import br.com.soat.config.configureRouting
import br.com.soat.config.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
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
            configureErrorHandling()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            server?.stop(1000, 2000)
        })
    }

    private fun Application.configureLogging() {
        install(CallLogging)
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