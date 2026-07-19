package br.com.soat.order.event.handler

import br.com.soat.event.EventEnvelope
import br.com.soat.event.EventType
import br.com.soat.event.InboundEventHandler
import br.com.soat.order.OrderStatusUseCase

class ExecutionFinishedHandler(
    private val orderStatusUseCase: OrderStatusUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.EXECUTION_FINISHED)

    override fun handle(envelope: EventEnvelope) {
        orderStatusUseCase.onExecutionFinished(envelope.orderId())
    }
}
