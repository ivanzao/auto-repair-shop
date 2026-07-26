package br.com.soat.order

import br.com.soat.customer.Customers
import br.com.soat.customer.toCustomer
import br.com.soat.order.model.Order
import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.shared.model.User
import br.com.soat.vehicle.Vehicles
import br.com.soat.vehicle.toVehicle
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Orders : Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val customerId = uuid("customer_id").references(Customers.id)
    val vehicleId = uuid("vehicle_id").references(Vehicles.id)

    val openedById = uuid("opened_by_id")
    val openedByDocument = varchar("opened_by_document", 20)

    val diagnosedById = uuid("diagnosed_by_id").nullable()
    val diagnosedByDocument = varchar("diagnosed_by_document", 20).nullable()

    val status = varchar("status", 50)
    val description = text("description")

    init {
        PrimaryKey(id)
    }
}

object OrderQuotedServices : Table("order_quoted_services") {
    val orderId = uuid("order_id").references(Orders.id)
    val serviceId = uuid("service_id")
    val name = varchar("name", 255)
    val price = decimal("price", 10, 2)

    init {
        PrimaryKey(orderId, serviceId)
    }
}

object OrderQuotedSupplies : Table("order_quoted_supplies") {
    val orderId = uuid("order_id").references(Orders.id)
    val supplyId = uuid("supply_id")
    val name = varchar("name", 255)
    val quantity = integer("quantity")
    val unitPrice = decimal("unit_price", 10, 2)

    init {
        PrimaryKey(orderId, supplyId)
    }
}

fun ResultRow.toOrder(
    services: List<QuotedService>,
    supplies: List<QuotedSupply>,
) = Order(
    id = this[Orders.id],
    createdAt = this[Orders.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Orders.modifiedAt].toJavaLocalDateTime(),
    status = Order.Status.valueOf(this[Orders.status]),
    version = this[Orders.version],
    customer = this.toCustomer(),
    vehicle = this.toVehicle(),
    openedBy = User(this[Orders.openedById], this[Orders.openedByDocument]),
    diagnosedBy = this[Orders.diagnosedById]?.let { User(it, this[Orders.diagnosedByDocument].orEmpty()) },
    services = services,
    supplies = supplies,
    description = this[Orders.description],
)
