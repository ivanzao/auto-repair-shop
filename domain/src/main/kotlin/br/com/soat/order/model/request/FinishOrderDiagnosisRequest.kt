package br.com.soat.order.model.request

import br.com.soat.supply.model.SupplyRequirement
import java.util.UUID

data class FinishOrderDiagnosisRequest(
    val orderId: UUID,
    val servicesIds: List<UUID>,
    val extraSupplyRequirements: List<SupplyRequirement>,
)
