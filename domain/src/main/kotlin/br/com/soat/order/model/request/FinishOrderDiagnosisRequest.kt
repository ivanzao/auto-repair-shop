package br.com.soat.order.model.request

import br.com.soat.supply.model.SupplyRequest
import java.util.UUID

data class FinishOrderDiagnosisRequest(
    val orderId: UUID,
    val servicesIds: List<UUID>,
    val extraSuppliesRequests: List<SupplyRequest>,
)
