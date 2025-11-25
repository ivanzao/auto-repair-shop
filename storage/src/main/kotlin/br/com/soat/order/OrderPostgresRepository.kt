package br.com.soat.order

import br.com.soat.customer.Customers
import br.com.soat.order.model.Order
import br.com.soat.service.ServiceSupplies
import br.com.soat.service.Services
import br.com.soat.service.toService
import br.com.soat.supply.model.SupplyRequest
import br.com.soat.user.Users
import br.com.soat.vehicle.Vehicles
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class OrderPostgresRepository : OrderRepository {

    override fun findById(id: UUID): Order? = transaction {
        val orderRow = Orders
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .join(Vehicles, JoinType.INNER, Orders.vehicleId, Vehicles.id)
            .join(Users, JoinType.INNER, Orders.attendantId, Users.id)
            .selectAll()
            .where { Orders.id eq id }
            .singleOrNull() ?: return@transaction null

        val servicesRows = OrderServices
            .join(Services, JoinType.INNER, OrderServices.serviceId, Services.id)
            .selectAll()
            .where { OrderServices.orderId eq id }
            .toList()

        val serviceIds = servicesRows.map { it[Services.id] }

        val serviceSupplies = ServiceSupplies
            .selectAll()
            .where { ServiceSupplies.serviceId inList serviceIds }
            .map { 
                it[ServiceSupplies.serviceId] to SupplyRequest(it[ServiceSupplies.supplyId], it[ServiceSupplies.quantity]) 
            }
            .groupBy({ it.first }, { it.second })

        val services = servicesRows.map { row ->
            val serviceId = row[Services.id]
            row.toService(serviceSupplies[serviceId] ?: emptyList())
        }

        val supplies = OrderSupplies
            .selectAll()
            .where { OrderSupplies.orderId eq id }
            .map { SupplyRequest(it[OrderSupplies.supplyId], it[OrderSupplies.quantity]) }

        orderRow.toOrder(services, supplies)
    }

    override fun create(order: Order): Order = transaction {
        Orders.insert {
            it[Orders.id] = order.id
            it[Orders.createdAt] = order.createdAt.toKotlinLocalDateTime()
            it[Orders.modifiedAt] = order.modifiedAt.toKotlinLocalDateTime()
            it[Orders.version] = order.version
            it[Orders.customerId] = order.customer.id
            it[Orders.vehicleId] = order.vehicle.id
            it[Orders.attendantId] = order.attendant.id
            it[Orders.status] = order.status.name
            it[Orders.description] = order.description
            it[Orders.technician] = order.technician
        }

        insertRelations(order)

        order
    }

    override fun update(order: Order): Order = transaction {
        Orders.update({ (Orders.id eq order.id) and (Orders.version eq order.version) }) {
            it[Orders.modifiedAt] = order.modifiedAt.toKotlinLocalDateTime()
            it[Orders.version] = order.version + 1
            it[Orders.status] = order.status.name
            it[Orders.description] = order.description
            it[Orders.technician] = order.technician
        }

        OrderServices.deleteWhere { OrderServices.orderId eq order.id }
        OrderSupplies.deleteWhere { OrderSupplies.orderId eq order.id }

        insertRelations(order)

        findById(order.id) ?: throw IllegalStateException("Order not found after update")
    }

    private fun insertRelations(order: Order) {
        if (order.services.isNotEmpty()) {
            OrderServices.batchInsert(order.services) { service ->
                this[OrderServices.orderId] = order.id
                this[OrderServices.serviceId] = service.id
            }
        }

        if (order.extraSupplies.isNotEmpty()) {
            OrderSupplies.batchInsert(order.extraSupplies) { supply ->
                this[OrderSupplies.orderId] = order.id
                this[OrderSupplies.supplyId] = supply.supplyId
                this[OrderSupplies.quantity] = supply.quantity
            }
        }
    }
}