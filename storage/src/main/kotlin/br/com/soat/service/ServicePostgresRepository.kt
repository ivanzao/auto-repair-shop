package br.com.soat.service

import br.com.soat.order.model.OrderService
import br.com.soat.order.repository.OrderServiceRepository
import br.com.soat.supply.model.SupplyRequest
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ServicePostgresRepository : OrderServiceRepository {

    override fun create(service: OrderService): OrderService = transaction {
        Services.insert {
            it[id] = service.id
            it[createdAt] = service.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = service.modifiedAt.toKotlinLocalDateTime()
            it[version] = service.version
            it[name] = service.name
            it[description] = service.description
            it[price] = service.price
        }.resultedValues?.singleOrNull()
            ?: throw IllegalStateException("An error occurred while saving Service")

        insertSupplies(service)

        service
    }

    override fun update(service: OrderService): OrderService = transaction {
        Services.update({ Services.id eq service.id }) {
            it[modifiedAt] = service.modifiedAt.toKotlinLocalDateTime()
            it[version] = service.version + 1
            it[name] = service.name
            it[description] = service.description
            it[price] = service.price
        }

        ServiceSupplies.deleteWhere { serviceId eq service.id }
        insertSupplies(service)
        
        findById(service.id) ?: throw IllegalStateException("Service not found after update")
    }

    override fun findById(id: UUID): OrderService? = transaction {
        val serviceRow = Services.selectAll()
            .where { Services.id eq id }
            .singleOrNull() ?: return@transaction null

        val supplies = ServiceSupplies
            .selectAll()
            .where { ServiceSupplies.serviceId eq id }
            .map { SupplyRequest(it[ServiceSupplies.supplyId], it[ServiceSupplies.quantity]) }

        serviceRow.toService(supplies)
    }

    override fun findAllByIds(servicesIds: List<UUID>): List<OrderService> = transaction {
        val servicesRows = Services.selectAll()
            .where { Services.id inList servicesIds }
            .toList()

        val allSupplies = ServiceSupplies
            .selectAll()
            .where { ServiceSupplies.serviceId inList servicesIds }
            .map { it[ServiceSupplies.serviceId] to SupplyRequest(it[ServiceSupplies.supplyId], it[ServiceSupplies.quantity]) }
            .groupBy({ it.first }, { it.second })

        servicesRows.map { row ->
            val serviceId = row[Services.id]
            row.toService(allSupplies[serviceId] ?: emptyList())
        }
    }

    private fun insertSupplies(service: OrderService) {
        if (service.requiredSupplies.isNotEmpty()) {
            ServiceSupplies.batchInsert(service.requiredSupplies) { supply ->
                this[ServiceSupplies.serviceId] = service.id
                this[ServiceSupplies.supplyId] = supply.supplyId
                this[ServiceSupplies.quantity] = supply.quantity
            }
        }
    }
}
