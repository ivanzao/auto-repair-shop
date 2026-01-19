package br.com.soat.auth

import br.com.soat.auth.dto.AuthenticateUserRequestDTO
import br.com.soat.auth.dto.AuthenticateUserResponseDTO
import br.com.soat.auth.dto.RefreshTokenRequestDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.authenticationRoutes(koin: Koin) {
    val useCase = koin.inject<LoginUseCase>().value

    routing {
        route("/v1") {
            post("/login") {
                val request = call.receive<AuthenticateUserRequestDTO>()
                val response = useCase.login(request.toModel())

                call.respond(
                    status = HttpStatusCode.Created,
                    message = AuthenticateUserResponseDTO.from(response)
                )
            }

            post("/refresh") {
                val request = call.receive<RefreshTokenRequestDTO>()
                val response = useCase.refresh(request.refreshToken)

                call.respond(
                    status = HttpStatusCode.Created,
                    message = AuthenticateUserResponseDTO.from(response)
                )
            }
        }
    }
}
