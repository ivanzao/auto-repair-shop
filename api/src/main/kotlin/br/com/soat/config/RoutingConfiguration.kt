package br.com.soat.config

import br.com.soat.auth.authenticationRoutes
import br.com.soat.customer.customerRoutes
import br.com.soat.order.orderRoutes
import br.com.soat.service.serviceRoutes
import br.com.soat.supply.supplyRoutes
import br.com.soat.user.userRoutes
import br.com.soat.vehicle.vehicleRoutes
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.configureRouting(koin: Koin) {
    routing {
        get("/health") {
            call.respondText("OK")
        }
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
    }

    userRoutes(koin)
    supplyRoutes(koin)
    serviceRoutes(koin)
    vehicleRoutes(koin)
    customerRoutes(koin)
    orderRoutes(koin)
    authenticationRoutes(koin)
}