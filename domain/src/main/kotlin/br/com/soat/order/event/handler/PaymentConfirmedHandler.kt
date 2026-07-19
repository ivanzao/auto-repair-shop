package br.com.soat.order.event.handler

import br.com.soat.event.EventEnvelope
import br.com.soat.event.EventType
import br.com.soat.event.InboundEventHandler
import br.com.soat.order.OrderStatusUseCase

class PaymentConfirmedHandler(
    private val orderStatusUseCase: OrderStatusUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.PAYMENT_CONFIRMED)

    override fun handle(envelope: EventEnvelope) {
        val amount = envelope.payload.get("amount")?.takeUnless { it.isNull }?.decimalValue()
        orderStatusUseCase.onPaymentConfirmed(envelope.orderId(), amount)
    }
}
