package br.com.soat.order

import br.com.soat.customer.Customers
import br.com.soat.customer.toCustomer
import br.com.soat.order.model.Order
import br.com.soat.service.Services
import br.com.soat.order.model.OrderService
import br.com.soat.supply.Supplies
import br.com.soat.supply.model.SupplyRequirement
import br.com.soat.user.Users
import br.com.soat.user.toUser
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
    val attendantId = uuid("attendant_id").references(Users.id)
    
    val status = varchar("status", 50)
    val description = text("description")
    val technician = varchar("technician", 255).nullable()

    init {
        PrimaryKey(id)
    }
}

object OrderServices : Table("order_services") {
    val orderId = uuid("order_id").references(Orders.id)
    val serviceId = uuid("service_id").references(Services.id)

    init {
        PrimaryKey(orderId, serviceId)
    }
}

object OrderSupplies : Table("order_supplies") {
    val orderId = uuid("order_id").references(Orders.id)
    val supplyId = uuid("supply_id").references(Supplies.id)
    val quantity = integer("quantity")

    init {
        PrimaryKey(orderId, supplyId)
    }
}

fun ResultRow.toOrder(
    services: List<OrderService>,
    supplies: List<SupplyRequirement>
) = Order(
    id = this[Orders.id],
    createdAt = this[Orders.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Orders.modifiedAt].toJavaLocalDateTime(),
    status = Order.Status.valueOf(this[Orders.status]),
    version = this[Orders.version],
    customer = this.toCustomer(),
    vehicle = this.toVehicle(),
    attendant = this.toUser(),
    services = services,
    extraSupplies = supplies,
    description = this[Orders.description],
    technician = this[Orders.technician]
)