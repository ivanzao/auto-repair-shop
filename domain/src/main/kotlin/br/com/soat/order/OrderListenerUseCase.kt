package br.com.soat.order

import br.com.soat.event.EventPublisher
import br.com.soat.event.model.DomainEvent
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.order.event.OrderAwaitingApprovalEvent
import br.com.soat.order.model.Order
import br.com.soat.order.model.logParams
import br.com.soat.order.model.request.FinishedDiagnosis
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.math.BigDecimal
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

class OrderListenerUseCase(
    private val orderRepository: OrderRepository,
    private val idempotency: IdempotencyRepository,
    private val outbox: OutboxRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler,
    private val metrics: OrderMetricsPort,
) {
    private val logger = LoggerFactory.getLogger(OrderListenerUseCase::class.java)

    fun awaitApproval(diagnosis: FinishedDiagnosis, idempotencyId: UUID) =
        handleStatusEvent(
            diagnosis.orderId,
            "DiagnoseFinished",
            idempotencyId,
            kv("reservationId", diagnosis.reservationId),
            kv("diagnosedById", diagnosis.diagnosedBy.id),
            kv("totalAmount", diagnosis.totalAmount),
            emit = { OrderAwaitingApprovalEvent.from(it, diagnosis.reservationId, diagnosis.totalAmount) },
        ) { it.awaitingApproval(diagnosis.diagnosedBy, diagnosis.services, diagnosis.supplies) }

    fun confirmPayment(orderId: UUID, amount: BigDecimal?, idempotencyId: UUID) =
        handleStatusEvent(orderId, "PaymentConfirmed", idempotencyId, kv("amount", amount)) { it.executionEnqueued() }

    fun startExecution(orderId: UUID, idempotencyId: UUID) =
        handleStatusEvent(orderId, "ExecutionStarted", idempotencyId) { it.inProgress() }

    fun finishExecution(orderId: UUID, idempotencyId: UUID) =
        handleStatusEvent(orderId, "ExecutionFinished", idempotencyId) { it.completed() }

    fun cancel(orderId: UUID, reason: String, idempotencyId: UUID) =
        handleStatusEvent(orderId, reason, idempotencyId) { it.canceled() }

    private fun handleStatusEvent(
        orderId: UUID,
        reason: String,
        idempotencyId: UUID,
        vararg extra: Any,
        emit: (Order) -> DomainEvent? = { null },
        change: (Order) -> Order,
    ) {
        if (idempotency.exists(orderId, idempotencyId)) {
            logger.info(
                "Skipping already-processed message",
                kv("orderId", orderId), kv("idempotencyId", idempotencyId), kv("reason", reason),
            )
            return
        }

        val order = orderRepository.findById(orderId)
        if (order == null) {
            logger.warn("Order {} not found while handling {}", orderId, reason)
            return
        }

        val updated = change(order)
        val changed = updated.status != order.status
        val event = if (changed) emit(updated) else null

        val stored = tx.inTransaction {
            if (changed) orderRepository.update(updated)
            idempotency.save(orderId, idempotencyId)
            event?.let { outbox.save(it) }
        }
        stored?.let { eventPublisher.publish(it) }

        metrics.inboundEventApplied()
        if (changed) {
            metrics.statusChanged(updated.status)
            logger.info(
                "Order status changed",
                *updated.logParams(
                    kv("event", "order.status_changed"),
                    kv("from_status", order.status.name),
                    kv("reason", reason),
                    *extra,
                ),
            )
        } else {
            logger.info(
                "Order status unchanged (out-of-order event)",
                *order.logParams(kv("reason", reason), *extra),
            )
        }
    }
}
