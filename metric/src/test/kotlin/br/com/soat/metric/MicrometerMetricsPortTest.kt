package br.com.soat.metric

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MicrometerMetricsPortTest {

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val port = MicrometerMetricsPort(registry)

    @Test
    fun `counter increments the underlying micrometer counter`() {
        val counter = port.counter("test_counter_total", "desc", mapOf("kind" to "unit"))
        counter.increment()
        counter.increment(3.0)

        val mc = registry.find("test_counter_total").tag("kind", "unit").counter()!!
        assertEquals(4.0, mc.count())
    }

    @Test
    fun `timer records duration`() {
        val timer = port.timer("test_timer_seconds", "desc")
        timer.record(150)

        val mt = registry.find("test_timer_seconds").timer()!!
        assertEquals(1L, mt.count())
    }

    @Test
    fun `timer recordSupplier returns the supplier result`() {
        val timer = port.timer("test_timer_supplier", "desc")
        val result = timer.recordSupplier { 42 }
        assertEquals(42, result)
    }
}
