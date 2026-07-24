package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.consumer.EventType
import br.com.soat.consumer.InboundEventHandler
import br.com.soat.order.OrderListenerUseCase

class PaymentConfirmedHandler(
    private val orderListenerUseCase: OrderListenerUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.PAYMENT_CONFIRMED)

    override fun handle(envelope: EventEnvelope) {
        val amount = envelope.payload.get("amount")?.takeUnless { it.isNull }?.decimalValue()
        orderListenerUseCase.confirmPayment(envelope.orderId(), amount, envelope.eventId)
    }
}
