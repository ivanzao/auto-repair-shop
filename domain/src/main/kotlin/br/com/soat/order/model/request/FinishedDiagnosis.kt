package br.com.soat.order.model.request

import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.shared.model.User
import java.math.BigDecimal
import java.util.UUID

data class FinishedDiagnosis(
    val orderId: UUID,
    val reservationId: UUID,
    val diagnosedBy: User,
    val services: List<QuotedService>,
    val supplies: List<QuotedSupply>,
    val totalAmount: BigDecimal,
)
