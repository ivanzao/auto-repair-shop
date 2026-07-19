package br.com.soat.event

import java.util.UUID

interface OutboxRepository {
    fun save(event: OutboxEvent): OutboxEvent
    fun findPending(limit: Int): List<OutboxEvent>
    fun markPublished(eventId: UUID)
}
