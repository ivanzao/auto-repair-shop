package br.com.soat.order.model

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

data class OrderSchedule(
    val id: UUID,
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val orderId: UUID,
    val dateTime: LocalDateTime,
    val type: Type
) {

    enum class Type {
        DELIVERY, RETURN
    }
}