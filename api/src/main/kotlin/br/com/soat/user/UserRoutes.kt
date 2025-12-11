package br.com.soat.user

import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.userRoutes(koin: Koin) {
    val userUseCase = koin.inject<UserUseCase>().value

    routing {
        authenticate("admin") {
            post("/users") {
                val request = call.receive<CreateUserRequestDTO>()
                val createdUser = userUseCase.create(request.toModel())

                call.respond(
                    status = HttpStatusCode.Created,
                    message = UserResponseDTO.from(createdUser)
                )
            }

            get("/users") {
                call.respondText("Hello from Koin")
            }
        }
    }
}