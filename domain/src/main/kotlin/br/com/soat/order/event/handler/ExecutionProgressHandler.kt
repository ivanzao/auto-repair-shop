package br.com.soat.order.event.handler

import br.com.soat.event.EventEnvelope
import br.com.soat.event.EventType
import br.com.soat.event.InboundEventHandler
import br.com.soat.order.OrderStatusUseCase

/** Status intermediários da execução — só observabilidade, sem transição. */
class ExecutionProgressHandler(
    private val orderStatusUseCase: OrderStatusUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.EXECUTION_STARTED, EventType.DIAGNOSE_FINISHED)

    override fun handle(envelope: EventEnvelope) {
        orderStatusUseCase.onExecutionProgress(envelope.orderId(), envelope.eventType)
    }
}
