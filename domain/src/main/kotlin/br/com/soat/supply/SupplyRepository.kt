package br.com.soat.supply

import br.com.soat.supply.model.Supply
import java.util.UUID

interface SupplyRepository {
    fun create(supply: Supply): Supply
    fun findAllByIds(suppliesIds: List<UUID>): List<Supply>
    fun updateAll(supplies: List<Supply>): List<Supply>
    fun findById(id: UUID): Supply?
    fun findAll(): List<Supply>
    fun update(supply: Supply): Supply
    fun delete(id: UUID): Boolean
}