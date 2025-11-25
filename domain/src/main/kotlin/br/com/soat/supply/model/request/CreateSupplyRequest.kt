package br.com.soat.supply.model.request

import java.math.BigDecimal

data class CreateSupplyRequest(
    val name: String,
    val description: String?,
    val quantity: Int,
    val price: BigDecimal
)