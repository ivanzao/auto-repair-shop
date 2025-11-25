package br.com.soat.supply

import br.com.soat.exception.OptimisticLockException
import br.com.soat.supply.model.Supply
import br.com.soat.user.Users
import java.time.LocalDateTime.now
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class SupplyPostgresRepository : SupplyRepository {

    override fun findAllByIds(suppliesIds: List<UUID>) = transaction {
        Supplies.selectAll()
            .where { Supplies.id inList suppliesIds }
            .map { it.toSupply() }
    }

    override fun create(supply: Supply) = transaction {
        Supplies.insert {
            it[id] = supply.id
            it[name] = supply.name
            it[description] = supply.description
            it[quantity] = supply.quantityInStock
            it[price] = supply.price
            it[createdAt] = supply.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = supply.modifiedAt.toKotlinLocalDateTime()
            it[version] = supply.version
        }.resultedValues?.singleOrNull()?.toSupply()
            ?: throw IllegalStateException("An error occurred while saving Supply")
    }

    override fun updateAll(supplies: List<Supply>) = transaction {
        val results = supplies.map { supply ->
            val result = Supplies.update({ (Supplies.id eq supply.id) and (Supplies.version eq supply.version) }) {
                it[name] = supply.name
                it[description] = supply.description
                it[quantity] = supply.quantityInStock
                it[price] = supply.price
                it[modifiedAt] = now().toKotlinLocalDateTime()
                it[version] = supply.version + 1
            }
            supply.id to result
        }

        results.filter { it.second == 0 }
            .takeIf { it.isNotEmpty() }
            ?.let {
                val failedIds = it.joinToString(", ") { supply -> supply.first.toString() }
                throw OptimisticLockException("Supplies $failedIds were modified by another transaction")
            }

        Supplies
            .selectAll()
            .where { Users.id inList supplies.map { it.id } }
            .map { it.toSupply() }
    }
}