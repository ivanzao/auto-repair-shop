package br.com.soat.order.dto

import br.com.soat.order.model.request.ScheduleOrderVehicleRequest
import java.time.ZonedDateTime
import java.util.UUID

data class OrderScheduleVehicleRequestDTO(
    val dateTime: ZonedDateTime
) {
    fun toModel(orderId: UUID) = ScheduleOrderVehicleRequest(
        orderId = orderId,
        dateTime = dateTime.toLocalDateTime()
    )
}