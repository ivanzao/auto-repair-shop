package br.com.soat.supply

import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.supply.dto.SupplyResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.supplyRoutes(koin: Koin) {
    val useCase = koin.inject<SupplyUseCase>().value

    routing {
        post("/supplies") {
            val request = call.receive<CreateSupplyRequestDTO>()
            val createdSupply = useCase.create(request.toModel())

            call.respond(
                status = HttpStatusCode.Created,
                message = SupplyResponseDTO.from(createdSupply)
            )
        }
    }
}

