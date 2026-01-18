package br.com.soat.order.model.request

import java.time.LocalDateTime
import java.util.UUID

data class ScheduleOrderVehicleRequest(
    val orderId: UUID,
    val dateTime: LocalDateTime,
)