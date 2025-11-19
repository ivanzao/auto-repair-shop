package br.com.soat.order

import br.com.soat.br.com.soat.order.OrderUseCase
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.OrderResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.orderRoutes() {
    val orderUseCase: OrderUseCase by inject()

    routing {
        post("/orders") {
            val request = call.receive<CreateOrderRequestDTO>()
            val createdOrder = orderUseCase.create(request.toModel())

            call.respond(
                status = HttpStatusCode.Created,
                message = OrderResponseDTO.from(createdOrder)
            )
        }
    }
}