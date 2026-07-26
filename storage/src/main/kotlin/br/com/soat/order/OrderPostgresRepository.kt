package br.com.soat.order

import br.com.soat.customer.Customers
import br.com.soat.exception.OptimisticLockException
import br.com.soat.order.model.Order
import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.model.Page
import br.com.soat.vehicle.Vehicles
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class OrderPostgresRepository : OrderRepository {

    override fun findAllPaginated(page: Int): Page<Order> = transaction {
        val limit = PAGE_SIZE
        val offset = (page - 1).toLong() * limit

        val excludedStatuses = listOf(
            Order.Status.COMPLETED.name,
            Order.Status.DELIVERED.name,
            Order.Status.CANCELED.name,
        )

        val statusPriority = Case()
            .When(Orders.status eq stringLiteral(Order.Status.IN_PROGRESS.name), intLiteral(1))
            .When(Orders.status eq stringLiteral(Order.Status.EXECUTION_ENQUEUED.name), intLiteral(2))
            .When(Orders.status eq stringLiteral(Order.Status.WAITING_APPROVAL.name), intLiteral(3))
            .When(Orders.status eq stringLiteral(Order.Status.RECEIVED.name), intLiteral(4))
            .Else(intLiteral(5))

        val orderRows = Orders
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .join(Vehicles, JoinType.INNER, Orders.vehicleId, Vehicles.id)
            .selectAll()
            .where { Orders.status notInList excludedStatuses }
            .orderBy(statusPriority to SortOrder.ASC, Orders.createdAt to SortOrder.ASC)
            .limit(limit)
            .offset(offset)
            .toList()

        val orderIds = orderRows.map { it[Orders.id] }
        val servicesByOrder = quotedServicesByOrder(orderIds)
        val suppliesByOrder = quotedSuppliesByOrder(orderIds)

        val orders = orderRows.map { row ->
            val orderId = row[Orders.id]
            row.toOrder(
                servicesByOrder[orderId] ?: emptyList(),
                suppliesByOrder[orderId] ?: emptyList(),
            )
        }

        Page(orders, page, limit)
    }

    override fun findById(id: UUID): Order? = transaction {
        val orderRow = Orders
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .join(Vehicles, JoinType.INNER, Orders.vehicleId, Vehicles.id)
            .selectAll()
            .where { Orders.id eq id }
            .singleOrNull() ?: return@transaction null

        orderRow.toOrder(
            quotedServicesByOrder(listOf(id))[id] ?: emptyList(),
            quotedSuppliesByOrder(listOf(id))[id] ?: emptyList(),
        )
    }

    override fun create(order: Order): Order = transaction {
        Orders.insert {
            it[Orders.id] = order.id
            it[Orders.createdAt] = order.createdAt.toKotlinLocalDateTime()
            it[Orders.modifiedAt] = order.modifiedAt.toKotlinLocalDateTime()
            it[Orders.version] = order.version
            it[Orders.customerId] = order.customer.id
            it[Orders.vehicleId] = order.vehicle.id
            it[Orders.openedById] = order.openedBy.id
            it[Orders.openedByDocument] = order.openedBy.document
            it[Orders.diagnosedById] = order.diagnosedBy?.id
            it[Orders.diagnosedByDocument] = order.diagnosedBy?.document
            it[Orders.status] = order.status.name
            it[Orders.description] = order.description
        }

        insertQuotedItems(order)

        order
    }

    override fun update(order: Order): Order = transaction {
        val rows = Orders.update({ (Orders.id eq order.id) and (Orders.version eq order.version) }) {
            it[Orders.modifiedAt] = order.modifiedAt.toKotlinLocalDateTime()
            it[Orders.version] = order.version + 1
            it[Orders.diagnosedById] = order.diagnosedBy?.id
            it[Orders.diagnosedByDocument] = order.diagnosedBy?.document
            it[Orders.status] = order.status.name
            it[Orders.description] = order.description
        }

        if (rows == 0) {
            throw OptimisticLockException(
                "Order ${order.id} was modified by another transaction (expected version ${order.version})"
            )
        }

        OrderQuotedServices.deleteWhere { OrderQuotedServices.orderId eq order.id }
        OrderQuotedSupplies.deleteWhere { OrderQuotedSupplies.orderId eq order.id }

        insertQuotedItems(order)

        findById(order.id) ?: throw IllegalStateException("Order not found after update")
    }

    private fun insertQuotedItems(order: Order) {
        if (order.services.isNotEmpty()) {
            OrderQuotedServices.batchInsert(order.services) { service ->
                this[OrderQuotedServices.orderId] = order.id
                this[OrderQuotedServices.serviceId] = service.id
                this[OrderQuotedServices.name] = service.name
                this[OrderQuotedServices.price] = service.price
            }
        }

        if (order.supplies.isNotEmpty()) {
            OrderQuotedSupplies.batchInsert(order.supplies) { supply ->
                this[OrderQuotedSupplies.orderId] = order.id
                this[OrderQuotedSupplies.supplyId] = supply.id
                this[OrderQuotedSupplies.name] = supply.name
                this[OrderQuotedSupplies.quantity] = supply.quantity
                this[OrderQuotedSupplies.unitPrice] = supply.unitPrice
            }
        }
    }

    private fun quotedServicesByOrder(orderIds: List<UUID>): Map<UUID, List<QuotedService>> {
        if (orderIds.isEmpty()) return emptyMap()
        return OrderQuotedServices
            .selectAll()
            .where { OrderQuotedServices.orderId inList orderIds }
            .groupBy({ it[OrderQuotedServices.orderId] }) {
                QuotedService(
                    id = it[OrderQuotedServices.serviceId],
                    name = it[OrderQuotedServices.name],
                    price = it[OrderQuotedServices.price],
                )
            }
    }

    private fun quotedSuppliesByOrder(orderIds: List<UUID>): Map<UUID, List<QuotedSupply>> {
        if (orderIds.isEmpty()) return emptyMap()
        return OrderQuotedSupplies
            .selectAll()
            .where { OrderQuotedSupplies.orderId inList orderIds }
            .groupBy({ it[OrderQuotedSupplies.orderId] }) {
                QuotedSupply(
                    id = it[OrderQuotedSupplies.supplyId],
                    name = it[OrderQuotedSupplies.name],
                    quantity = it[OrderQuotedSupplies.quantity],
                    unitPrice = it[OrderQuotedSupplies.unitPrice],
                )
            }
    }

    companion object {
        private const val PAGE_SIZE = 100
    }
}
