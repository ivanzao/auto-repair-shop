package br.com.soat.order.model.request

import br.com.soat.shared.model.User
import java.util.UUID

data class CreateOrderRequest(
    val openedBy: User,

    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
)
