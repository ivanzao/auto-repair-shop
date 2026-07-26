package br.com.soat.order.event

import br.com.soat.event.model.DomainEvent
import br.com.soat.order.model.Order
import java.math.BigDecimal
import java.util.UUID

class OrderAwaitingApprovalEvent(
    val orderId: UUID,
    val reservationId: UUID,
    val customer: Customer,
    val services: List<ServiceLine>,
    val supplies: List<SupplyLine>,
    val totalAmount: BigDecimal,
) : DomainEvent() {

    override val eventType = ORDER_AWAITING_APPROVAL

    data class Customer(val name: String, val email: String)
    data class ServiceLine(val id: UUID, val name: String, val price: BigDecimal)
    data class SupplyLine(val id: UUID, val name: String, val quantity: Int, val unitPrice: BigDecimal)

    companion object {
        const val ORDER_AWAITING_APPROVAL = "OrderAwaitingApproval"

        fun from(order: Order, reservationId: UUID, totalAmount: BigDecimal) = OrderAwaitingApprovalEvent(
            orderId = order.id,
            reservationId = reservationId,
            customer = Customer(order.customer.name, order.customer.email.value),
            services = order.services.map { ServiceLine(it.id, it.name, it.price) },
            supplies = order.supplies.map { SupplyLine(it.id, it.name, it.quantity, it.unitPrice) },
            totalAmount = totalAmount,
        )
    }
}
