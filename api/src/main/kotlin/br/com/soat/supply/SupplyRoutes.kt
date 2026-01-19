package br.com.soat.supply

import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.supply.dto.SupplyResponseDTO
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

fun Application.supplyRoutes(koin: Koin) {
    val useCase = koin.inject<SupplyUseCase>().value

    routing {
        route("/v1") {
            authenticate("admin") {
                post("/supplies") {
                    val request = call.receive<CreateSupplyRequestDTO>()
                    val createdSupply = useCase.create(request.toModel())

                    call.respond(
                        status = HttpStatusCode.Created,
                        message = SupplyResponseDTO.from(createdSupply)
                    )
                }

                get("/supplies/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val supply = useCase.findById(id)

                    if (supply != null) {
                        call.respond(HttpStatusCode.OK, SupplyResponseDTO.from(supply))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/supplies") {
                    val supplies = useCase.findAll()
                    call.respond(HttpStatusCode.OK, supplies.map { SupplyResponseDTO.from(it) })
                }

                put("/supplies/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<CreateSupplyRequestDTO>()
                    val updatedSupply = useCase.update(id, request.toModel())

                    if (updatedSupply != null) {
                        call.respond(HttpStatusCode.OK, SupplyResponseDTO.from(updatedSupply))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/supplies/{id}") {
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
