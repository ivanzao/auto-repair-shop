package br.com.soat.user

import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UpdateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import br.com.soat.shared.getUUIDPathParameter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.userRoutes(koin: Koin) {
    val userUseCase = koin.inject<UserUseCase>().value

    routing {
        route("/v1") {
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
                    val users = userUseCase.findAll()
                    call.respond(HttpStatusCode.OK, users.map { UserResponseDTO.from(it) })
                }

                get("/users/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val user = userUseCase.findById(id)

                    if (user != null) {
                        call.respond(HttpStatusCode.OK, UserResponseDTO.from(user))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                put("/users/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<UpdateUserRequestDTO>()
                    val updatedUser = userUseCase.update(id, request.toModel())

                    if (updatedUser != null) {
                        call.respond(HttpStatusCode.OK, UserResponseDTO.from(updatedUser))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/users/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val deleted = userUseCase.delete(id)

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
