package br.com.soat.order.model.request

import java.util.UUID

data class CreateOrderRequest(
    val attendantId: UUID,

    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
    val technician: String? = null,
)