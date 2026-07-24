package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.consumer.EventType
import br.com.soat.consumer.InboundEventHandler
import br.com.soat.order.OrderListenerUseCase

class ExecutionProgressHandler(
    private val orderListenerUseCase: OrderListenerUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.EXECUTION_STARTED, EventType.DIAGNOSE_FINISHED)

    override fun handle(envelope: EventEnvelope) {
        orderListenerUseCase.recordExecutionProgress(envelope.orderId(), envelope.eventType)
    }
}
