package br.com.soat.supply.dto

import br.com.soat.supply.model.Supply
import java.math.BigDecimal

data class SupplyResponseDTO(
    val name: String,
    val description: String?,
    val quantity: Int,
    val price: BigDecimal,
) {
    companion object {
        fun from(supply: Supply) = SupplyResponseDTO(
            name = supply.name,
            description = supply.description,
            quantity = supply.quantityInStock,
            price = supply.price
        )
    }
}