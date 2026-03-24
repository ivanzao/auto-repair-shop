package br.com.soat.order.dto

import br.com.soat.order.model.request.CreateOrderRequest
import java.util.UUID

data class CreateOrderRequestDTO(
    val customerId: UUID,
    val vehicleId: UUID,

    val description: String,
    val technician: String? = null,

    val attendantId: UUID,

    val servicesIds: List<UUID>,
    val extraSuppliesRequests: List<SupplyRequirementDTO> = emptyList(),
) {
    fun toModel() = CreateOrderRequest(
        customerId = customerId,
        vehicleId = vehicleId,
        description = description,
        technician = technician,
        attendantId = attendantId,
        servicesIds = servicesIds,
        extraSupplyRequirements = extraSuppliesRequests.map { it.toModel() },
    )
}