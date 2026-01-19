package br.com.soat.order.dto

import br.com.soat.order.model.OrderMetrics

data class OrderMetricsResponseDTO(
    val totalCompleted: Long,
    val averageExecutionTimeSeconds: Long?
) {
    companion object {
        fun from(metrics: OrderMetrics) = OrderMetricsResponseDTO(
            totalCompleted = metrics.totalCompleted,
            averageExecutionTimeSeconds = metrics.averageExecutionTime?.seconds
        )
    }
}
