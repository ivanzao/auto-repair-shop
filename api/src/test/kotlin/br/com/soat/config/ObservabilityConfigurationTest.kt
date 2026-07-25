package br.com.soat.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservabilityConfigurationTest {

    @Test
    fun `binds JVM and process meters that exist without waiting for a first GC`() {
        val scraped = prometheusMeterRegistry().scrape()

        listOf(
            "jvm_memory_used_bytes",
            "jvm_memory_max_bytes",
            "jvm_gc_memory_allocated_bytes_total",
            "jvm_threads_live_threads",
            "process_cpu_usage",
        ).forEach { name ->
            assertTrue(scraped.contains(name), "$name ausente no scrape")
        }
    }
}
