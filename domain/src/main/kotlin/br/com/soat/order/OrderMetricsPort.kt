package br.com.soat.order

import br.com.soat.order.model.Order

interface OrderMetricsPort {
    fun orderCreated()
    fun statusChanged(status: Order.Status)
    fun inboundEventApplied()
}
