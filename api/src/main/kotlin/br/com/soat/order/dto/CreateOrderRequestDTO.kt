package br.com.soat.order.dto

import br.com.soat.order.model.request.CreateOrderRequest
import java.util.UUID

data class CreateOrderRequestDTO(
    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
    val serviceIds: List<UUID>,
    val technician: String? = null,

    val attendantId: UUID,
) {
    fun toModel() = CreateOrderRequest(
        customerId = customerId,
        vehicleId = vehicleId,
        description = description,
        serviceIds = serviceIds,
        technician = technician,
        attendantId = attendantId
    )
}