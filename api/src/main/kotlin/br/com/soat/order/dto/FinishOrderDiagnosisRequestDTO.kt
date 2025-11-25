package br.com.soat.order.dto

import br.com.soat.shared.dto.SupplyRequestDTO
import java.util.UUID

data class FinishOrderDiagnosisRequestDTO(
    val servicesIds: List<UUID>,
    val extraSuppliesRequests: List<SupplyRequestDTO>,
)