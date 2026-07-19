package br.com.soat.event

import br.com.soat.event.model.EventStatus
import java.time.Instant
import java.util.UUID

/**
 * Evento de integração pendente de publicação (padrão outbox). Guardado na mesma
 * transação da mudança de estado que o originou e publicado depois pelo relay.
 * [payload] é o JSON do payload do evento (o envelope é montado na publicação).
 */
data class OutboxEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val eventVersion: Int = 1,
    val occurredAt: Instant = Instant.now(),
    val payload: String,
    val status: EventStatus = EventStatus.PENDING,
)
