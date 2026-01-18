package br.com.soat.order.dto

import br.com.soat.order.model.Order
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.UUID

data class OrderStatusResponseDTO(
    val id: UUID,
    val status: Order.Status,
    val modifiedAt: ZonedDateTime
) {
    companion object {
        fun from(order: Order) = OrderStatusResponseDTO(
            id = order.id,
            status = order.status,
            modifiedAt = order.modifiedAt.atZone(UTC)
        )
    }
}
