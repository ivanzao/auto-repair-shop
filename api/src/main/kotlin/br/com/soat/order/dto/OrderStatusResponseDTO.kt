package br.com.soat.order.dto

import br.com.soat.order.model.Order
import java.time.LocalDateTime
import java.util.UUID

data class OrderStatusResponseDTO(
    val id: UUID,
    val status: Order.Status,
    val modifiedAt: LocalDateTime
) {
    companion object {
        fun from(order: Order) = OrderStatusResponseDTO(
            id = order.id,
            status = order.status,
            modifiedAt = order.modifiedAt
        )
    }
}
