package br.com.soat.supply.model.event

import br.com.soat.event.model.DomainEvent
import br.com.soat.supply.model.SupplyRequest
import java.time.LocalDateTime
import java.util.UUID

data class OrderSupplyRequested(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val modifiedAt: LocalDateTime = LocalDateTime.now(),
    override val version: Int = 0,

    val orderId: UUID,
    val supplyRequests: List<SupplyRequest>
) : DomainEvent
