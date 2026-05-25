package br.com.soat.attendant

import br.com.soat.attendant.dto.AttendantResponseDTO
import br.com.soat.attendant.dto.CreateAttendantRequestDTO
import br.com.soat.attendant.dto.UpdateAttendantRequestDTO
import br.com.soat.shared.getUUIDPathParameter
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
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.attendantRoutes(koin: Koin) {
    val useCase = koin.inject<AttendantUseCase>().value

    routing {
        route("/v1") {
            authenticate("admin") {
                post("/attendants") {
                    val req = call.receive<CreateAttendantRequestDTO>()
                    val created = useCase.create(req.toModel())
                    call.respond(HttpStatusCode.Created, AttendantResponseDTO.from(created))
                }

                get("/attendants") {
                    val all = useCase.findAll()
                    call.respond(HttpStatusCode.OK, all.map { AttendantResponseDTO.from(it) })
                }

                get("/attendants/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    call.respond(HttpStatusCode.OK, AttendantResponseDTO.from(useCase.findById(id)))
                }

                put("/attendants/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val req = call.receive<UpdateAttendantRequestDTO>()
                    call.respond(HttpStatusCode.OK, AttendantResponseDTO.from(useCase.update(id, req.toModel())))
                }

                delete("/attendants/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    useCase.delete(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
