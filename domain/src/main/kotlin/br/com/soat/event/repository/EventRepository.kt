package br.com.soat.event.repository

import br.com.soat.event.model.DomainEvent
import br.com.soat.event.model.EventStatus
import java.util.UUID

interface EventRepository {
    fun findPendingEvents(limit: Int): List<DomainEvent>
    fun findAllBy(type: String, status: EventStatus, limit: Int): List<DomainEvent>
    fun isProcessed(eventId: UUID, consumerId: String): Boolean
    fun save(event: DomainEvent): DomainEvent
    fun markAsProcessed(eventId: UUID, consumerId: String)
    fun updateStatus(id: UUID, status: EventStatus)
}
