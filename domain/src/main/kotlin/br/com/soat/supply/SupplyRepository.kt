package br.com.soat.supply

import br.com.soat.supply.model.Supply
import java.util.UUID

interface SupplyRepository {
    fun create(supply: Supply): Supply
    fun findAllByIds(suppliesIds: List<UUID>): List<Supply>
    fun updateAll(supplies: List<Supply>): List<Supply>
}