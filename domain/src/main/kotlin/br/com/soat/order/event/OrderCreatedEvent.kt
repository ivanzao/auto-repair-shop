package br.com.soat.order.event

import br.com.soat.event.model.DomainEvent
import br.com.soat.order.model.Order
import java.util.UUID

class OrderCreatedEvent(
    val orderId: UUID,
    val customer: Customer,
    val vehicle: Vehicle,
) : DomainEvent() {

    override val eventType = ORDER_CREATED

    data class Customer(val id: UUID, val name: String, val email: String)
    data class Vehicle(val plate: String, val model: String)

    companion object {
        const val ORDER_CREATED = "OrderCreated"

        fun from(order: Order) = OrderCreatedEvent(
            orderId = order.id,
            customer = Customer(order.customer.id, order.customer.name, order.customer.email.value),
            vehicle = Vehicle(order.vehicle.plate.value, order.vehicle.model),
        )
    }
}
