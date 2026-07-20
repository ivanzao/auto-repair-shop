package br.com.soat.order

import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.OrderMetricsResponseDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.dto.OrderStatusResponseDTO
import br.com.soat.auth.JwtUserPrincipal
import br.com.soat.shared.dto.PageResponseDTO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import br.com.soat.shared.getUUIDPathParameter
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.orderRoutes(koin: Koin) {
    val orderUseCase = koin.inject<OrderUseCase>().value

    routing {
        route("/v1") {
            get("/orders/{id}/status") {
                val id = call.getUUIDPathParameter("id")
                val order = orderUseCase.findById(id)

                if (order != null) {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = OrderStatusResponseDTO.from(order)
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            authenticate("attendant") {
                get("/orders") {
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val ordersPage = orderUseCase.findAll(page)
                    call.respond(HttpStatusCode.OK, PageResponseDTO.from(ordersPage, OrderResponseDTO::from))
                }

                get("/orders/metrics") {
                    val metrics = orderUseCase.getMetrics()
                    call.respond(HttpStatusCode.OK, OrderMetricsResponseDTO.from(metrics))
                }

                post("/orders") {
                    val principal = call.principal<JwtUserPrincipal>()!!
                    val request = call.receive<CreateOrderRequestDTO>()
                    val createdOrder = orderUseCase.create(request.toModel(attendantId = principal.userId))

                    call.respond(
                        status = HttpStatusCode.Created,
                        message = OrderResponseDTO.from(createdOrder)
                    )
                }

                post("/orders/{id}/schedule-delivery") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<OrderScheduleVehicleRequestDTO>()
                    orderUseCase.scheduleVehicleDelivery(request.toModel(id))

                    call.respond(HttpStatusCode.OK)
                }

                post("/orders/{id}/schedule-return") {
                    val id = call.getUUIDPathParameter("id")
                    val request = call.receive<OrderScheduleVehicleRequestDTO>()
                    orderUseCase.scheduleVehicleReturn(request.toModel(id))

                    call.respond(HttpStatusCode.OK)
                }

                post("/orders/{id}/deliver") {
                    val id = call.getUUIDPathParameter("id")
                    val deliveredOrder = orderUseCase.deliver(id)

                    call.respond(
                        status = HttpStatusCode.OK,
                        message = OrderResponseDTO.from(deliveredOrder)
                    )
                }

                get("/orders/{id}") {
                    val id = call.getUUIDPathParameter("id")
                    val order = orderUseCase.findById(id)

                    if (order != null) {
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = OrderResponseDTO.from(order)
                        )
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
