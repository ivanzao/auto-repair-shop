package br.com.soat.order.dto

import java.util.UUID

data class FinishOrderDiagnosisRequestDTO(
    val servicesIds: List<UUID>,
    val extraSuppliesRequests: List<SupplyRequirementDTO>,
)