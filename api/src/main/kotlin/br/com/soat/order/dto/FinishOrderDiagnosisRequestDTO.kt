package br.com.soat.order.dto

import java.util.UUID

data class FinishOrderDiagnosisRequestDTO(
    val servicesIds: List<UUID> = emptyList(),
    val extraSuppliesRequests: List<SupplyRequirementDTO> = emptyList(),
)