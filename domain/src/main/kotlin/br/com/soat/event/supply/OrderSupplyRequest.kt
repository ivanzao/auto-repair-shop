package br.com.soat.event.supply

import br.com.soat.shared.DomainEvent
import br.com.soat.supply.model.SupplyRequest
import java.time.LocalDateTime
import java.util.UUID

data class OrderSupplyRequest(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val modifiedAt: LocalDateTime = LocalDateTime.now(),
    override val version: Int = 0,

    val orderId: UUID,
    val supplyRequests: List<SupplyRequest>
) : DomainEvent
