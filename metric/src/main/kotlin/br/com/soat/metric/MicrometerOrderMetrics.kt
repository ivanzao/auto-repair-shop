package br.com.soat.metric

import br.com.soat.order.OrderMetricsPort
import br.com.soat.order.model.Order
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class MicrometerOrderMetrics(registry: MeterRegistry) : OrderMetricsPort {

    private val created: Counter = Counter.builder("orders_created_total")
        .description("Total de ordens de serviço criadas")
        .register(registry)

    private val byStatus: Map<Order.Status, Counter> =
        Order.Status.entries.associateWith { status ->
            Counter.builder("orders_by_status_total")
                .description("Total de transições de ordens de serviço para cada status")
                .tag("status", status.name)
                .register(registry)
        }

    private val inboundEvents: Counter = Counter.builder("order_inbound_events_total")
        .description("Total de eventos de integração aplicados ao status da OS")
        .register(registry)

    override fun orderCreated() = created.increment()

    override fun statusChanged(status: Order.Status) {
        byStatus.getValue(status).increment()
    }

    override fun inboundEventApplied() = inboundEvents.increment()
}
