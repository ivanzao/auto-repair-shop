package br.com.soat.order.model.request

import java.util.UUID

data class StartOrderDiagnosisRequest(
    val orderId: UUID,
    val technician: String,
)
