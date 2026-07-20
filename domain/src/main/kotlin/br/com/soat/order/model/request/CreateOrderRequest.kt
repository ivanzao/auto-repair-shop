package br.com.soat.order.model.request

import br.com.soat.shared.model.SupplyRequirement
import java.util.UUID

data class CreateOrderRequest(
    val attendantId: UUID,

    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
    val technician: String? = null,

    val servicesIds: List<UUID>,
    val extraSupplyRequirements: List<SupplyRequirement> = emptyList(),
)