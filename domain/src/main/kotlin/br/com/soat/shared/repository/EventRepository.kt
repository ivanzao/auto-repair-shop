package br.com.soat.shared.repository

import br.com.soat.shared.DomainEvent
import br.com.soat.shared.EventStatus
import java.util.UUID

interface EventRepository {
    fun save(event: DomainEvent): DomainEvent
    fun findPendingEvents(limit: Int): List<DomainEvent>
    fun findAllBy(type: String, status: EventStatus, limit: Int): List<DomainEvent>
    fun existsByPayload(type: String, partialPayload: String): Boolean
    fun markAsProcessed(eventId: UUID, consumerId: String)
    fun isProcessed(eventId: UUID, consumerId: String): Boolean
    fun updateStatus(id: UUID, status: EventStatus)
}