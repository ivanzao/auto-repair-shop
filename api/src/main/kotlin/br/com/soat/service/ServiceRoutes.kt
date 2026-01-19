package br.com.soat.service

import br.com.soat.service.dto.CreateServiceRequestDTO
import br.com.soat.service.dto.ServiceResponseDTO
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

fun Application.serviceRoutes(koin: Koin) {
    val useCase = koin.inject<ServiceUseCase>().value

    routing {
        route("/v1") {
            authenticate("admin") {
                post("/services") {
                    val request = call.receive<CreateServiceRequestDTO>()
                    val createdService = useCase.create(request.toModel())

                    call.respond(
                        status = HttpStatusCode.Created,
                        message = ServiceResponseDTO.from(createdService)
                    )
                }

                get("/services/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val service = useCase.findById(id)

                    if (service != null) {
                        call.respond(HttpStatusCode.OK, ServiceResponseDTO.from(service))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/services") {
                    val services = useCase.findAll()
                    call.respond(HttpStatusCode.OK, services.map { ServiceResponseDTO.from(it) })
                }

                put("/services/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<CreateServiceRequestDTO>()
                    try {
                        val updatedService = useCase.update(id, request.toModel())
                        call.respond(HttpStatusCode.OK, ServiceResponseDTO.from(updatedService))
                    } catch (e: IllegalStateException) {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/services/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val deleted = useCase.delete(id)

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
