package br.com.soat.order

import br.com.soat.command.CommandPublisher
import br.com.soat.command.repository.CommandRepository
import br.com.soat.order.command.SendQuoteToClientCommand
import br.com.soat.mail.EmailService
import br.com.soat.mail.model.OrderQuoteApprovalEmailInput
import br.com.soat.order.exception.OrderNotFoundException
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderApprovalToken
import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.repository.SupplyRepository
import java.time.LocalDateTime
import java.util.UUID

class OrderListenerUseCase(
    private val supplyRepository: SupplyRepository,
    private val orderRepository: OrderRepository,
    private val commandRepository: CommandRepository,
    private val commandPublisher: CommandPublisher,
    private val emailService: EmailService,
    private val orderApprovalTokenRepository: OrderApprovalTokenRepository,
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository,
    private val tx: RepositoryTransactionHandler
) {

    fun sendQuoteToApproval(orderId: UUID) {
        val order = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)

        val command = tx.inTransaction {
            orderRepository.update(order.waitingApproval())
            commandRepository.save(SendQuoteToClientCommand(orderId = orderId, idempotencyId = orderId))
        }

        commandPublisher.publish(command)
    }

    fun sendQuoteApprovalEmail(orderId: UUID) {
        val order = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)

        val approvalToken = orderApprovalTokenRepository.save(
            OrderApprovalToken(
                orderId = orderId,
                expiresAt = LocalDateTime.now().plusDays(5)
            )
        )

        val requiredSupplies = order.getSupplyRequirements()
        val supplies = supplyRepository.findAllByIds(requiredSupplies.map { it.supplyId })
        val email = OrderQuoteApprovalEmailInput(
            callbackToken = approvalToken.id.toString(),
            customerName = order.customer.name,
            customerEmail = order.customer.email.value,
            services = order.services,
            supplies = supplies.map {
                OrderQuoteApprovalEmailInput.Supply(
                    name = it.name,
                    price = it.price,
                    quantity = requiredSupplies.single {
                        requirement -> requirement.supplyId == it.id
                    }.quantity,
                )
            }
        )

        emailService.sendOrderQuoteApprovalEmail(email)
    }

    fun registerExecutionTimeMetric(orderId: UUID, status: Order.Status) {
        when (status) {
            Order.Status.IN_PROGRESS -> {
                orderExecutionMetricRepository.create(
                    OrderExecutionMetric(
                        orderId = orderId,
                        inProgressAt = LocalDateTime.now()
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
            else -> { }
        }
    }
}
