package br.com.soat.supply

import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.EventRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.exception.InsufficientStockException
import br.com.soat.supply.exception.SupplyNotFoundException
import br.com.soat.supply.model.Supply
import br.com.soat.supply.model.SupplyRequirement
import br.com.soat.supply.model.event.OrderSuppliesReservedEvent
import br.com.soat.supply.repository.SupplyRepository
import java.util.UUID

class SupplyStockService(
    private val orderRepository: OrderRepository,
    private val supplyRepository: SupplyRepository,
    private val eventRepository: EventRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler
) {

    fun reserveSuppliesForOrder(orderId: UUID) {
        val order = orderRepository.findById(orderId)
            ?: throw IllegalArgumentException("Order not found $orderId")

        val supplyRequirements = order.getSupplyRequirements()
        val supplies = supplyRepository.findAllByIds(supplyRequirements.map { it.supplyId })

        validateRequiredSuppliesExists(supplies, supplyRequirements)

        val updatedSupplies = supplies.map { supply ->
            val supplyRequirement = supplyRequirements.single { request -> request.supplyId == supply.id }
            if (supplyRequirement.quantity > supply.quantityInStock) {
                throw InsufficientStockException(supply.id)
            }

            supply.copy(quantityInStock = supply.quantityInStock - supplyRequirement.quantity)
        }

        val event = tx.inTransaction {
            supplyRepository.updateAll(updatedSupplies)
            eventRepository.save(OrderSuppliesReservedEvent(orderId = orderId))
        }

        eventPublisher.publish(event)
    }

    private fun validateRequiredSuppliesExists(foundSupplies: List<Supply>, supplyRequirements: List<SupplyRequirement>) {
        supplyRequirements.map { it.supplyId }
            .firstOrNull { it !in foundSupplies.map { service -> service.id } }
            ?.let { throw SupplyNotFoundException(it) }
    }
}