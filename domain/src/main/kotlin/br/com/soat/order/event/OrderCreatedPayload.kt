package br.com.soat.order.event

import br.com.soat.order.model.Order
import java.math.BigDecimal
import java.util.UUID

/**
 * Payload lean do evento `OrderCreated` (order → execution). Carrega apenas o que
 * o order é dono: serviços já precificados e as referências de peças (id + quantidade).
 * Execution resolve nome/preço das peças no próprio catálogo e recomputa o total no
 * `PartsReserved`. Sem `unitPrice`/`name` de peça nem `totalAmount` (ver Anexo A do plano).
 */
data class OrderCreatedPayload(
    val orderId: UUID,
    val customer: Customer,
    val vehicle: Vehicle,
    val services: List<ServiceLine>,
    val parts: List<PartLine>,
) {
    data class Customer(val id: UUID, val name: String, val email: String)
    data class Vehicle(val plate: String, val model: String)
    data class ServiceLine(val id: UUID, val name: String, val price: BigDecimal)
    data class PartLine(val id: UUID, val quantity: Int)

    companion object {
        fun from(order: Order) = OrderCreatedPayload(
            orderId = order.id,
            customer = Customer(order.customer.id, order.customer.name, order.customer.email.value),
            vehicle = Vehicle(order.vehicle.plate.value, order.vehicle.model),
            services = order.services.map { ServiceLine(it.id, it.name, it.price) },
            parts = order.getSupplyRequirements().map { PartLine(it.supplyId, it.quantity) },
        )
    }
}
