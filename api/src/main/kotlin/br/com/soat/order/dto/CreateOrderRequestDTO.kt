package br.com.soat.order.dto

import br.com.soat.br.com.soat.order.model.CreateOrderRequest
import br.com.soat.br.com.soat.user.model.User
import java.util.UUID

data class CreateOrderRequestDTO(
    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
    val serviceIds: List<UUID>,
    val technician: String? = null,
) {
    fun toModel() = CreateOrderRequest(
        customerId = customerId,
        vehicleId = vehicleId,
        description = description,
        serviceIds = serviceIds,
        technician = technician,
        attendant = User(
            name = "Kenny Savage",
            document = "affert",
            email = "olive.lowe@example.com",
            contact = "semper",
            role = User.Role.ADMIN
        )
    )
}