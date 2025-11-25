package br.com.soat.supply

import br.com.soat.supply.model.Supply
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Supplies : Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val name = varchar("name", 255)
    val description = varchar("description", 255).nullable()
    val quantity = integer("quantity")
    val price = decimal("price", 10, 2)

    init {
        PrimaryKey(id)
    }
}

fun ResultRow.toSupply(): Supply = Supply(
    id = this[Supplies.id],
    createdAt = this[Supplies.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Supplies.modifiedAt].toJavaLocalDateTime(),
    version = this[Supplies.version],
    name = this[Supplies.name],
    description = this[Supplies.description],
    quantityInStock = this[Supplies.quantity],
    price = this[Supplies.price]
)