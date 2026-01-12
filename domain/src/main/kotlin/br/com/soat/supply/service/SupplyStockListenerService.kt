package br.com.soat.supply.service

import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.EventRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.SupplyRepository
import br.com.soat.supply.model.Supply
import br.com.soat.supply.model.SupplyRequest
import br.com.soat.supply.model.event.OrderSuppliesReservedEvent
import java.util.UUID

class SupplyStockListenerService(
    private val orderRepository: OrderRepository,
    private val supplyRepository: SupplyRepository,
    private val eventRepository: EventRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler
) {

    fun reserveSuppliesForOrder(orderId: UUID) {
        val order = orderRepository.findById(orderId)
            ?: throw IllegalArgumentException("Order not found $orderId")

        val supplyRequests = order.getRequiredSupplyRequests()
        val supplies = supplyRepository.findAllByIds(supplyRequests.map { it.supplyId })

        validateRequestedSuppliesExists(supplies, supplyRequests)

        val updatedSupplies = supplies.map { supply ->
            val supplyRequest = supplyRequests.single { request -> request.supplyId == supply.id }
            if (supplyRequest.quantity > supply.quantityInStock) {
                throw IllegalStateException("Insufficient stock for supply ${supply.name}")
            }

            supply.copy(quantityInStock = supply.quantityInStock - supplyRequest.quantity)
        }

        val event = tx.inTransaction {
            supplyRepository.updateAll(updatedSupplies)
            eventRepository.save(OrderSuppliesReservedEvent(orderId = orderId))
        }

        eventPublisher.publish(event)
    }

    private fun validateRequestedSuppliesExists(foundSupplies: List<Supply>, supplyRequests: List<SupplyRequest>) {
        supplyRequests.map { it.supplyId }
            .filter { it !in foundSupplies.map { service -> service.id } }
            .takeIf { it.isNotEmpty() }
            ?.let { throw IllegalArgumentException("Requested services not found: ${it.joinToString(", ")}") }
    }
}
