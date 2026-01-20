package br.com.soat.supply.model.event.handler

import br.com.soat.event.handler.EventHandler
import br.com.soat.event.model.DomainEvent
import br.com.soat.order.event.OrderDiagnoseFinishedEvent
import br.com.soat.supply.SupplyStockService
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class OrderDiagnoseFinishedEventHandler(
    private val supplyStockListenerService: SupplyStockService
) : EventHandler {

    private val logger = LoggerFactory.getLogger(OrderDiagnoseFinishedEventHandler::class.java)

    override val eventType: KClass<out DomainEvent> = OrderDiagnoseFinishedEvent::class

    override fun handle(event: DomainEvent) {
        if (event !is OrderDiagnoseFinishedEvent) return
        logger.info("Processing OrderDiagnoseFinishedEvent for Order ${event.orderId}")
        supplyStockListenerService.reserveSuppliesForOrder(event.orderId)
    }
}