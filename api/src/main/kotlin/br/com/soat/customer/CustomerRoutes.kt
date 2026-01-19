package br.com.soat.customer

import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
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
import br.com.soat.shared.getUUIDPathParameter
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.customerRoutes(koin: Koin) {
    val customerUseCase = koin.inject<CustomerUseCase>().value

    routing {
        route("/v1") {
            authenticate("admin") {
                post("/customers") {
                    val request = call.receive<CreateCustomerRequestDTO>()
                    val createdCustomer = customerUseCase.create(request.toModel())

                    call.respond(
                        status = HttpStatusCode.Created,
                        message = CustomerResponseDTO.from(createdCustomer)
                    )
                }

                get("/customers/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val customer = customerUseCase.findById(id)

                    if (customer != null) {
                        call.respond(HttpStatusCode.OK, CustomerResponseDTO.from(customer))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/customers") {
                    val customers = customerUseCase.findAll()
                    call.respond(HttpStatusCode.OK, customers.map { CustomerResponseDTO.from(it) })
                }

                put("/customers/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<CreateCustomerRequestDTO>()
                    val updatedCustomer = customerUseCase.update(id, request.toModel())

                    if (updatedCustomer != null) {
                        call.respond(HttpStatusCode.OK, CustomerResponseDTO.from(updatedCustomer))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/customers/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val deleted = customerUseCase.delete(id)

                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
