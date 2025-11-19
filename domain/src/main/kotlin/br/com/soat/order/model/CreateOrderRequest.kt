package br.com.soat.br.com.soat.order.model

import br.com.soat.br.com.soat.user.model.User
import java.util.UUID

data class CreateOrderRequest(
    val attendant: User,

    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
    val serviceIds: List<UUID>,
    val technician: String? = null,
)