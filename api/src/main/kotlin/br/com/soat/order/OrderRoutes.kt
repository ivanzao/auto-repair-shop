package br.com.soat.order

import br.com.soat.order.dto.OrderQuoteApprovalRequestDTO
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.FinishOrderDiagnosisRequestDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.dto.OrderStatusResponseDTO
import br.com.soat.order.dto.StartOrderDiagnosisRequestDTO
import br.com.soat.order.model.request.FinishOrderDiagnosisRequest
import br.com.soat.order.model.request.StartOrderDiagnosisRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.UUID
import org.koin.core.Koin

fun Application.orderRoutes(koin: Koin) {
    val orderUseCase = koin.inject<OrderUseCase>().value

    routing {
        post("/orders/quote/approve") {
            val request = call.receive<OrderQuoteApprovalRequestDTO>()
            orderUseCase.approveQuote(request.approvalToken)

            call.respond(HttpStatusCode.OK)
        }

        post("/orders/quote/decline") {
            val request = call.receive<OrderQuoteApprovalRequestDTO>()
            orderUseCase.declineQuote(request.approvalToken)

            call.respond(HttpStatusCode.OK)
        }

        get("/orders/{id}/status") {
            val orderId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Order ID must be present")

            val orderUuid = try {
                UUID.fromString(orderId)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.BadRequest, "Invalid Order ID format")
            }

            val order = orderUseCase.findById(orderUuid)

            if (order != null) {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = OrderStatusResponseDTO.from(order)
                )
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        authenticate("admin") {
            post("/orders") {
                val request = call.receive<CreateOrderRequestDTO>()
                val createdOrder = orderUseCase.create(request.toModel())

                call.respond(
                    status = HttpStatusCode.Created,
                    message = OrderResponseDTO.from(createdOrder)
                )
            }

            post("/orders/{id}/start-diagnosis") {
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("OrderId must be present")
                val request = call.receive<StartOrderDiagnosisRequestDTO>()
                val createdOrder = orderUseCase.startDiagnosis(
                    StartOrderDiagnosisRequest(
                        orderId = UUID.fromString(orderId),
                        technician = request.technician
                    )
                )

                call.respond(
                    status = HttpStatusCode.OK,
                    message = OrderResponseDTO.from(createdOrder)
                )
            }

            post("/orders/{id}/finish-diagnosis") {
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("OrderId must be present")
                val request = call.receive<FinishOrderDiagnosisRequestDTO>()
                val createdOrder = orderUseCase.finishDiagnosis(
                    FinishOrderDiagnosisRequest(
                        orderId = UUID.fromString(orderId),
                        servicesIds = request.servicesIds,
                        extraSuppliesRequests = request.extraSuppliesRequests.map { it.toModel() },
                    )
                )

                call.respond(
                    status = HttpStatusCode.OK,
                    message = OrderResponseDTO.from(createdOrder)
                )
            }

            post("/orders/{id}/schedule-delivery") {
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("OrderId must be present")
                val request = call.receive<OrderScheduleVehicleRequestDTO>()
                orderUseCase.scheduleVehicleDelivery(request.toModel(UUID.fromString(orderId)))

                call.respond(HttpStatusCode.OK)
            }

            post("/orders/{id}/schedule-return") {
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("OrderId must be present")
                val request = call.receive<OrderScheduleVehicleRequestDTO>()
                orderUseCase.scheduleVehicleReturn(request.toModel(UUID.fromString(orderId)))

                call.respond(HttpStatusCode.OK)
            }

            get("/orders/{id}") {
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("OrderId must be present")
                val order = orderUseCase.findById(UUID.fromString(orderId))

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