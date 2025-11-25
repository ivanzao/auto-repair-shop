package br.com.soat.customer

import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.customerRoutes(koin: Koin) {
    val customerUseCase = koin.inject<CustomerUseCase>().value

    routing {
        post("/customers") {
            val request = call.receive<CreateCustomerRequestDTO>()
            val createdOrder = customerUseCase.create(request.toModel())

            call.respond(
                status = HttpStatusCode.Created,
                message = CustomerResponseDTO.from(createdOrder)
            )
        }
    }
}