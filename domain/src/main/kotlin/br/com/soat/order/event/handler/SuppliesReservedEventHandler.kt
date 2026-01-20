package br.com.soat.order.event.handler

import br.com.soat.event.handler.EventHandler
import br.com.soat.event.model.DomainEvent
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.supply.model.event.OrderSuppliesReservedEvent
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class SuppliesReservedEventHandler(
    private val orderListenerUseCase: OrderListenerUseCase,
) : EventHandler {

    private val logger = LoggerFactory.getLogger(SuppliesReservedEventHandler::class.java)

    override val eventType: KClass<out DomainEvent> = OrderSuppliesReservedEvent::class

    override fun handle(event: DomainEvent) {
        if (event !is OrderSuppliesReservedEvent) return
        logger.info("Processing SuppliesReservedEvent for Order ${event.orderId}")
        orderListenerUseCase.sendQuoteToApproval(event.orderId)
    }
}
