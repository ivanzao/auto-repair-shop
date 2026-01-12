package br.com.soat.order.dto

import br.com.soat.supply.model.SupplyRequest
import java.util.UUID

data class SupplyRequestDTO(
    val supplyId: UUID,
    val quantity: Int,
) {

    fun toModel() = SupplyRequest(
        supplyId = supplyId,
        quantity = quantity
    )
}