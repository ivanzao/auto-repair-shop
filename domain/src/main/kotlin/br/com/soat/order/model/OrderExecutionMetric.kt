package br.com.soat.order.model

import java.time.LocalDateTime
import java.util.UUID
import java.util.UUID.randomUUID

data class OrderExecutionMetric(
    val id: UUID = randomUUID(),
    val orderId: UUID,
    val inProgressAt: LocalDateTime,
    val completedAt: LocalDateTime? = null
)
