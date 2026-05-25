package br.com.soat.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Application.configureObservability(registry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        this.registry = registry
    }

    routing {
        get("/metrics") { call.respond(registry.scrape()) }
    }
}

fun prometheusMeterRegistry(): PrometheusMeterRegistry =
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
