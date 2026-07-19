package br.com.soat.supply.model

import java.util.UUID

data class SupplyRequirement(
    val supplyId: UUID,
    val quantity: Int,
)
