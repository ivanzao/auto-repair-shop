package br.com.soat.supply

import br.com.soat.exception.OptimisticLockException
import br.com.soat.supply.model.Supply
import br.com.soat.supply.repository.SupplyRepository
import java.time.LocalDateTime.now
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class SupplyPostgresRepository : SupplyRepository {

    override fun findById(id: UUID) = transaction {
        Supplies.selectAll()
            .where { Supplies.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toSupply()
    }

    override fun findAll(): List<Supply> = transaction {
        Supplies.selectAll().map { it.toSupply() }
    }

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

    override fun update(supply: Supply): Supply = transaction {
        Supplies.update({ Supplies.id eq supply.id }) {
            it[version] = supply.version + 1
            it[modifiedAt] = supply.modifiedAt.toKotlinLocalDateTime()

            it[name] = supply.name
            it[description] = supply.description
            it[quantity] = supply.quantityInStock
            it[price] = supply.price
            it[modifiedAt] = supply.modifiedAt.toKotlinLocalDateTime()
            it[version] = supply.version
        }

        findById(supply.id) ?: throw IllegalStateException("Supply not found after update")
    }

    override fun updateAll(supplies: List<Supply>) = transaction {
        val results = supplies.map { supply ->
            val result = Supplies.update({ (Supplies.id eq supply.id) and (Supplies.version eq supply.version) }) {
                it[modifiedAt] = now().toKotlinLocalDateTime()
                it[version] = supply.version + 1

                it[name] = supply.name
                it[description] = supply.description
                it[quantity] = supply.quantityInStock
                it[price] = supply.price
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
            .where { Supplies.id inList supplies.map { it.id } }
            .map { it.toSupply() }
    }

    override fun delete(id: UUID) {
        transaction { Supplies.deleteWhere { Supplies.id eq id } } }
}