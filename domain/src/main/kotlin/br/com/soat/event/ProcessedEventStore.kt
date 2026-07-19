package br.com.soat.event

import java.util.UUID

/**
 * Registro de idempotência por `(eventId, consumerId)`, sobre a tabela
 * `processed_events`. Desacopla o [InboundEventDispatcher] do outbox.
 */
interface ProcessedEventStore {
    fun isProcessed(eventId: UUID, consumerId: String): Boolean
    fun markAsProcessed(eventId: UUID, consumerId: String)
}
