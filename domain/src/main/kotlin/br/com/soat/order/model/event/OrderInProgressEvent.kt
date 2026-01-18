package br.com.soat.order.model.event

import br.com.soat.event.model.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

data class OrderInProgressEvent(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val modifiedAt: LocalDateTime = LocalDateTime.now(),
    override val version: Int = 0,

    val orderId: UUID
) : DomainEvent