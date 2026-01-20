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
                    call.respond(HttpStatusCode.OK, ServiceResponseDTO.from(service))
                }

                get("/services") {
                    val services = useCase.findAll()
                    call.respond(HttpStatusCode.OK, services.map { ServiceResponseDTO.from(it) })
                }

                put("/services/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<CreateServiceRequestDTO>()
                    val updatedService = useCase.update(id, request.toModel())
                    call.respond(HttpStatusCode.OK, ServiceResponseDTO.from(updatedService))
                }

                delete("/services/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    useCase.delete(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
