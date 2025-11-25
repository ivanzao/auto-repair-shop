package br.com.soat.supply.model

import java.util.UUID

data class SupplyRequest(
    val supplyId: UUID,
    val quantity: Int,
)