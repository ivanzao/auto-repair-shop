package br.com.soat.worker.event.handler

import br.com.soat.event.supply.OrderSuppliesReservedEvent
import br.com.soat.order.OrderUseCase
import br.com.soat.shared.DomainEvent
import br.com.soat.worker.event.EventHandler
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class SuppliesReservedEventHandler(
    private val orderUseCase: OrderUseCase,
) : EventHandler {
    
    private val logger = LoggerFactory.getLogger(SuppliesReservedEventHandler::class.java)

    override val eventType: KClass<out DomainEvent> = OrderSuppliesReservedEvent::class

    override fun handle(event: DomainEvent) {
        if (event !is OrderSuppliesReservedEvent) return
        logger.info("Processing SuppliesReservedEvent for Order ${event.orderId}")
        orderUseCase.sendQuoteToApproval(event.orderId)
    }
}
