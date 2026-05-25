package br.com.soat.order

import br.com.soat.command.CommandPublisher
import br.com.soat.command.repository.CommandRepository
import br.com.soat.order.command.SendQuoteEmailCommand
import br.com.soat.order.exception.OrderNotFoundException
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderApprovalToken
import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.repository.SupplyRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class OrderListenerUseCase(
    private val supplyRepository: SupplyRepository,
    private val orderRepository: OrderRepository,
    private val commandRepository: CommandRepository,
    private val commandPublisher: CommandPublisher,
    private val orderApprovalTokenRepository: OrderApprovalTokenRepository,
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository,
    private val tx: RepositoryTransactionHandler,
) {

    fun sendQuoteToApproval(orderId: UUID) {
        val order = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)

        val requiredSupplies = order.getSupplyRequirements()
        val supplies = supplyRepository.findAllByIds(requiredSupplies.map { it.supplyId })

        val command = tx.inTransaction {
            orderRepository.update(order.waitingApproval())

            val approvalToken = orderApprovalTokenRepository.save(
                OrderApprovalToken(
                    orderId = orderId,
                    expiresAt = LocalDateTime.now().plusDays(5),
                )
            )

            val totalServices = order.services.sumOf { it.price }
            val totalSupplies = supplies.sumOf {
                val qty = requiredSupplies.single { req -> req.supplyId == it.id }.quantity
                it.price * BigDecimal(qty)
            }

            commandRepository.save(
                SendQuoteEmailCommand(
                    orderId = orderId,
                    callbackToken = approvalToken.id.toString(),
                    customerEmail = order.customer.email.value,
                    customerName = order.customer.name,
                    totalAmount = totalServices + totalSupplies,
                    services = order.services.map {
                        SendQuoteEmailCommand.Service(name = it.name, price = it.price)
                    },
                    supplies = supplies.map {
                        SendQuoteEmailCommand.Supply(
                            name = it.name,
                            quantity = requiredSupplies.single { req -> req.supplyId == it.id }.quantity,
                            unitPrice = it.price,
                        )
                    },
                )
            )
        }

        commandPublisher.publish(command)
    }

    fun registerExecutionTimeMetric(orderId: UUID, status: Order.Status) {
        when (status) {
            Order.Status.IN_PROGRESS -> {
                orderExecutionMetricRepository.create(
                    OrderExecutionMetric(
                        orderId = orderId,
                        inProgressAt = LocalDateTime.now(),
                    )
                )
            }
            Order.Status.COMPLETED -> {
                val existingMetric = orderExecutionMetricRepository.findByOrderId(orderId)
                if (existingMetric != null) {
                    orderExecutionMetricRepository.update(
                        existingMetric.copy(completedAt = LocalDateTime.now())
                    )
                }
            }
            else -> {}
        }
    }
}
