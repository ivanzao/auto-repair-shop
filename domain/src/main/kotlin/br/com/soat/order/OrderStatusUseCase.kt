package br.com.soat.order

import br.com.soat.metric.MetricsPort
import br.com.soat.order.model.Order
import br.com.soat.order.repository.OrderRepository
import java.math.BigDecimal
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

/**
 * Aplica ao status da OS os eventos de integração consumidos de billing/execution.
 * As transições são idempotentes: eventos repetidos ou fora de ordem viram no-op.
 */
class OrderStatusUseCase(
    private val orderRepository: OrderRepository,
    metrics: MetricsPort,
) {
    private val logger = LoggerFactory.getLogger(OrderStatusUseCase::class.java)

    private val eventsApplied: MetricsPort.Counter = metrics.counter(
        name = "order_inbound_events_total",
        description = "Total de eventos de integração aplicados ao status da OS",
    )

    /** PaymentConfirmed → OS paga (IN_PROGRESS). O valor pago é apenas registrado em log. */
    fun onPaymentConfirmed(orderId: UUID, amount: BigDecimal?) =
        transition(orderId, "PaymentConfirmed", amount) { it.markInProgress() }

    /** ExecutionFinished → OS concluída (COMPLETED). */
    fun onExecutionFinished(orderId: UUID) =
        transition(orderId, "ExecutionFinished") { it.markCompleted() }

    /** Falhas/compensações → OS cancelada (CANCELED). */
    fun onCanceled(orderId: UUID, eventType: String) =
        transition(orderId, eventType) { it.markCanceled() }

    /** ExecutionStarted/DiagnoseFinished → apenas observabilidade, sem transição. */
    fun onExecutionProgress(orderId: UUID, eventType: String) {
        eventsApplied.increment()
        logger.info(
            "Order execution progress",
            kv("event", "order.progress"),
            kv("orderId", orderId),
            kv("eventType", eventType),
        )
    }

    private fun transition(orderId: UUID, eventType: String, amount: BigDecimal? = null, apply: (Order) -> Order) {
        val order = orderRepository.findById(orderId)
        if (order == null) {
            logger.warn("Order {} not found while handling {}", orderId, eventType)
            return
        }
        eventsApplied.increment()
        val updated = apply(order)
        if (updated.status != order.status) {
            orderRepository.update(updated)
            logger.info(
                "Order status changed",
                kv("event", "order.status_changed"),
                kv("orderId", orderId),
                kv("from_status", order.status.name),
                kv("to_status", updated.status.name),
                kv("eventType", eventType),
                kv("amount", amount),
            )
        } else {
            logger.info(
                "Order status unchanged (idempotent)",
                kv("orderId", orderId),
                kv("current_status", order.status.name),
                kv("eventType", eventType),
            )
        }
    }
}
