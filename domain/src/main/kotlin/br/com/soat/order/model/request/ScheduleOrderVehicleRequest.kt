package br.com.soat.order.model.request

import java.time.LocalDateTime
import java.util.UUID

data class ScheduleOrderVehicleRequest(
    val orderId: UUID,
    val vehicleId: UUID,
    val dateTime: LocalDateTime,
)