package br.com.soat.order.event.handler

import br.com.soat.event.handler.EventHandler
import br.com.soat.event.model.DomainEvent
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.model.Order
import br.com.soat.order.event.OrderInProgressEvent
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class OrderInProgressEventHandler(
    private val useCase: OrderListenerUseCase,
) : EventHandler {

    private val logger = LoggerFactory.getLogger(OrderInProgressEventHandler::class.java)

    override val eventType: KClass<out DomainEvent> = OrderInProgressEvent::class

    override fun handle(event: DomainEvent) {
        if (event !is OrderInProgressEvent) return
        logger.info("Processing OrderInProgressEvent for Order ${event.orderId}")
        useCase.registerExecutionTimeMetric(event.orderId, Order.Status.IN_PROGRESS)
    }
}
