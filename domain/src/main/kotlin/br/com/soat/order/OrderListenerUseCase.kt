package br.com.soat.order

import br.com.soat.order.model.Order
import br.com.soat.order.model.logParams
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
    private val tx: RepositoryTransactionHandler,
    private val metrics: OrderMetricsPort,
) {
    private val logger = LoggerFactory.getLogger(OrderListenerUseCase::class.java)

    fun confirmPayment(orderId: UUID, amount: BigDecimal?, idempotencyId: UUID) =
        handleStatusEvent(orderId, "PaymentConfirmed", idempotencyId, kv("amount", amount)) { it.inProgress() }

    fun finishExecution(orderId: UUID, idempotencyId: UUID) =
        handleStatusEvent(orderId, "ExecutionFinished", idempotencyId) { it.completed() }

    fun cancel(orderId: UUID, reason: String, idempotencyId: UUID) =
        handleStatusEvent(orderId, reason, idempotencyId) { it.canceled() }

    fun recordExecutionProgress(orderId: UUID, step: String) {
        metrics.inboundEventApplied()
        logger.info(
            "Order execution progress",
            kv("event", "order.progress"),
            kv("orderId", orderId),
            kv("step", step),
        )
    }

    private fun handleStatusEvent(
        orderId: UUID,
        reason: String,
        idempotencyId: UUID,
        vararg extra: Any,
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
        tx.inTransaction {
            if (updated.status != order.status) orderRepository.update(updated)
            idempotency.save(orderId, idempotencyId)
        }

        metrics.inboundEventApplied()
        if (updated.status != order.status) {
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
