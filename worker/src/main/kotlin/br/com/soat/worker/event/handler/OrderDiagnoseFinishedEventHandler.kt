package br.com.soat.worker.event.handler

import br.com.soat.event.EventHandler
import br.com.soat.order.model.event.OrderDiagnoseFinishedEvent
import br.com.soat.event.model.DomainEvent
import br.com.soat.supply.service.SupplyStockService
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class OrderDiagnoseFinishedEventHandler(
    private val supplyStockService: SupplyStockService
) : EventHandler {

    private val logger = LoggerFactory.getLogger(OrderDiagnoseFinishedEventHandler::class.java)

    override val eventType: KClass<out DomainEvent> = OrderDiagnoseFinishedEvent::class

    override fun handle(event: DomainEvent) {
        if (event !is OrderDiagnoseFinishedEvent) return
        logger.info("Processing OrderDiagnoseFinishedEvent for Order ${event.orderId}")
        supplyStockService.reserveSuppliesForOrder(event.orderId)
    }
}
