package br.com.soat.order.dto

import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.shared.model.User
import java.util.UUID

data class CreateOrderRequestDTO(
    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
) {
    fun toModel(openedBy: User) = CreateOrderRequest(
        openedBy = openedBy,
        customerId = customerId,
        vehicleId = vehicleId,
        description = description,
    )
}
