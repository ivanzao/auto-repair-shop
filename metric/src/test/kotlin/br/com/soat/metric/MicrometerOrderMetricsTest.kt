package br.com.soat.metric

import br.com.soat.order.model.Order
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `exports orders_total not orders_created_total, since _created is a reserved suffix`() {
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val metrics = MicrometerOrderMetrics(prometheus)

        metrics.orderCreated()
        metrics.statusChanged(Order.Status.IN_PROGRESS)
        metrics.inboundEventApplied()

        val scraped = prometheus.scrape()

        assertTrue(scraped.contains("# TYPE orders_total counter"), "orders_total ausente")
        assertTrue(scraped.contains("# TYPE orders_by_status_total counter"), "orders_by_status_total ausente")
        assertTrue(scraped.contains("# TYPE order_inbound_events_total counter"), "order_inbound_events_total ausente")
        assertFalse(scraped.contains("orders_created"), "orders_created nao sobrevive ao scrape; nao use em query")
    }
}
