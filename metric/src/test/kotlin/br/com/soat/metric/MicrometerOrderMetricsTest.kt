package br.com.soat.metric

import br.com.soat.order.model.Order
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MicrometerOrderMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = MicrometerOrderMetrics(registry)

    @Test
    fun `orderCreated increments orders_created_total`() {
        metrics.orderCreated()
        metrics.orderCreated()
        assertEquals(2.0, registry.get("orders_created_total").counter().count())
    }

    @Test
    fun `statusChanged increments the counter tagged with the status`() {
        metrics.statusChanged(Order.Status.IN_PROGRESS)
        metrics.statusChanged(Order.Status.IN_PROGRESS)
        metrics.statusChanged(Order.Status.CANCELED)

        assertEquals(
            2.0,
            registry.get("orders_by_status_total").tag("status", "IN_PROGRESS").counter().count(),
        )
        assertEquals(
            1.0,
            registry.get("orders_by_status_total").tag("status", "CANCELED").counter().count(),
        )
    }

    @Test
    fun `inboundEventApplied increments order_inbound_events_total`() {
        metrics.inboundEventApplied()
        assertEquals(1.0, registry.get("order_inbound_events_total").counter().count())
    }
}
