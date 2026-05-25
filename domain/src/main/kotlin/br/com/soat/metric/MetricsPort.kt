package br.com.soat.metric

interface MetricsPort {

    fun counter(
        name: String,
        description: String = "",
        tags: Map<String, String> = emptyMap(),
    ): Counter

    fun timer(
        name: String,
        description: String = "",
        tags: Map<String, String> = emptyMap(),
    ): Timer

    interface Counter {
        fun increment(amount: Double = 1.0)
    }

    interface Timer {
        fun record(durationMs: Long)
        fun <T> recordSupplier(block: () -> T): T
    }
}
