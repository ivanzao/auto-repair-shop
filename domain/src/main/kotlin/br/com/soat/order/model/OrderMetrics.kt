package br.com.soat.order.model

import java.time.Duration

data class OrderMetrics(
    val totalCompleted: Long,
    val averageExecutionTime: Duration?
)
