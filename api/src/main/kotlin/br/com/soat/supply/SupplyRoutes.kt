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
            authenticate("attendant") {
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

                    call.respond(HttpStatusCode.OK, SupplyResponseDTO.from(supply))
                }

                get("/supplies") {
                    val supplies = useCase.findAll()

                    call.respond(HttpStatusCode.OK, supplies.map { SupplyResponseDTO.from(it) })
                }

                put("/supplies/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<CreateSupplyRequestDTO>()
                    val updatedSupply = useCase.update(id, request.toModel())

                    call.respond(HttpStatusCode.OK, SupplyResponseDTO.from(updatedSupply))
                }

                delete("/supplies/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    useCase.delete(id)

                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
