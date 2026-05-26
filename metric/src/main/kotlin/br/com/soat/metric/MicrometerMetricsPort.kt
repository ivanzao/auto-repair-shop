package br.com.soat.metric

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.Duration

class MicrometerMetricsPort(private val registry: MeterRegistry) : MetricsPort {

    override fun counter(name: String, description: String, tags: Map<String, String>): MetricsPort.Counter {
        val counter = io.micrometer.core.instrument.Counter
            .builder(name)
            .description(description)
            .tags(tags.toMicrometerTags())
            .register(registry)

        return MicrometerCounter(counter)
    }

    override fun timer(name: String, description: String, tags: Map<String, String>): MetricsPort.Timer {
        val timer = io.micrometer.core.instrument.Timer
            .builder(name)
            .description(description)
            .tags(tags.toMicrometerTags())
            .register(registry)

        return MicrometerTimer(timer)
    }

    private class MicrometerCounter(private val mc: io.micrometer.core.instrument.Counter) : MetricsPort.Counter {
        override fun increment(amount: Double) = mc.increment(amount)
    }

    private class MicrometerTimer(private val mt: io.micrometer.core.instrument.Timer) : MetricsPort.Timer {
        override fun record(durationMs: Long) = mt.record(Duration.ofMillis(durationMs))
        override fun <T> recordSupplier(block: () -> T): T = mt.recordCallable(block)!!
    }

    private fun Map<String, String>.toMicrometerTags(): List<Tag> =
        map { Tag.of(it.key, it.value) }
}
