package br.com.soat.vehicle

import br.com.soat.vehicle.dto.CreateVehicleRequestDTO
import br.com.soat.vehicle.dto.VehicleResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.vehicleRoutes(koin: Koin) {
    val useCase = koin.inject<VehicleUseCase>().value

    routing {
        authenticate("admin") {
            post("/vehicles") {
                val request = call.receive<CreateVehicleRequestDTO>()
                val createdVehicle = useCase.create(request.toModel())

                call.respond(
                    status = HttpStatusCode.Created,
                    message = VehicleResponseDTO.from(createdVehicle)
                )
            }

            get("/vehicles/{id}") {
                val id = call.parameters["id"]?.let { java.util.UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID format")

                val vehicle = useCase.findById(id)

                if (vehicle != null) {
                    call.respond(HttpStatusCode.OK, VehicleResponseDTO.from(vehicle))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/vehicles") {
                val vehicles = useCase.findAll()
                call.respond(HttpStatusCode.OK, vehicles.map { VehicleResponseDTO.from(it) })
            }

            put("/vehicles/{id}") {
                val id = call.parameters["id"]?.let { java.util.UUID.fromString(it) }
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid ID format")

                val request = call.receive<CreateVehicleRequestDTO>()
                val updatedVehicle = useCase.update(id, request.toModel())

                if (updatedVehicle != null) {
                    call.respond(HttpStatusCode.OK, VehicleResponseDTO.from(updatedVehicle))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/vehicles/{id}") {
                val id = call.parameters["id"]?.let { java.util.UUID.fromString(it) }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid ID format")

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