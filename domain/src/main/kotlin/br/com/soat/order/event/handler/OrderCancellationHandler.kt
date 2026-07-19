package br.com.soat.order.event.handler

import br.com.soat.event.EventEnvelope
import br.com.soat.event.EventType
import br.com.soat.event.InboundEventHandler
import br.com.soat.order.OrderStatusUseCase

/** Todas as falhas/compensações que levam a OS a CANCELED. */
class OrderCancellationHandler(
    private val orderStatusUseCase: OrderStatusUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(
        EventType.QUOTE_REJECTED,
        EventType.PAYMENT_FAILED,
        EventType.PARTS_UNAVAILABLE,
        EventType.EXECUTION_FAILED,
        EventType.RESERVATION_EXPIRED,
    )

    override fun handle(envelope: EventEnvelope) {
        orderStatusUseCase.onCanceled(envelope.orderId(), envelope.eventType)
    }
}
