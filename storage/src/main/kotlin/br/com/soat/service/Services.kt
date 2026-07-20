package br.com.soat.service

import br.com.soat.service.model.Service
import br.com.soat.shared.model.SupplyRequirement
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Services : Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val name = varchar("name", 255)
    val description = text("description").nullable()
    val price = decimal("price", 10, 2)

    init {
        PrimaryKey(id)
    }
}

object ServiceSupplies : Table("service_supplies") {
    val serviceId = uuid("service_id").references(Services.id)
    val supplyId = uuid("supply_id")
    val quantity = integer("quantity")

    init {
        PrimaryKey(serviceId, supplyId)
    }
}

fun ResultRow.toService(supplies: List<SupplyRequirement>) = Service(
    id = this[Services.id],
    createdAt = this[Services.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Services.modifiedAt].toJavaLocalDateTime(),
    name = this[Services.name],
    description = this[Services.description],
    price = this[Services.price],
    requiredSupplies = supplies
)
