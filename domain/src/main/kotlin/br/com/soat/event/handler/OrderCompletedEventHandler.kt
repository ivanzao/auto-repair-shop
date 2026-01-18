package br.com.soat.event.handler

import br.com.soat.event.EventHandler
import br.com.soat.event.model.DomainEvent
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.model.Order
import br.com.soat.order.model.event.OrderCompletedEvent
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class OrderCompletedEventHandler(
    private val useCase: OrderListenerUseCase
) : EventHandler {

    private val logger = LoggerFactory.getLogger(OrderCompletedEventHandler::class.java)

    override val eventType: KClass<out DomainEvent> = OrderCompletedEvent::class

    override fun handle(event: DomainEvent) {
        if (event !is OrderCompletedEvent) return
        logger.info("Processing OrderCompletedEvent for Order ${event.orderId}")
        useCase.registerExecutionTimeMetric(event.orderId, Order.Status.COMPLETED)
    }
}
