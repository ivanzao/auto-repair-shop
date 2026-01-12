package br.com.soat.order.dto

import br.com.soat.order.model.request.ScheduleOrderVehicleRequest
import java.time.LocalDateTime
import java.util.UUID

data class OrderScheduleVehicleRequestDTO(
    val vehicleId: UUID,
    val dateTime: LocalDateTime
) {
    fun toModel(orderId: UUID) = ScheduleOrderVehicleRequest(
        orderId = orderId,
        vehicleId = vehicleId,
        dateTime = dateTime
    )
}