package br.com.soat.order.model

import java.math.BigDecimal
import java.util.UUID

data class QuotedService(
    val id: UUID,
    val name: String,
    val price: BigDecimal,
)

data class QuotedSupply(
    val id: UUID,
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
)
