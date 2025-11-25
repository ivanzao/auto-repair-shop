package br.com.soat.vehicle

import br.com.soat.vehicle.dto.CreateVehicleRequestDTO
import br.com.soat.vehicle.dto.VehicleResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.vehicleRoutes(koin: Koin) {
    val useCase = koin.inject<VehicleUseCase>().value

    routing {
        post("/vehicles") {
            val request = call.receive<CreateVehicleRequestDTO>()
            val createdVehicle = useCase.create(request.toModel())

            call.respond(
                status = HttpStatusCode.Created,
                message = VehicleResponseDTO.from(createdVehicle)
            )
        }
    }
}