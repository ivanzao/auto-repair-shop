package br.com.soat.event

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/**
 * Envelope dos eventos de integração trocados via SNS/SQS, conforme o contrato
 * compartilhado. É o formato que trafega no body da SQS (raw message delivery)
 * e no message da SNS.
 */
data class EventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val payload: JsonNode,
)
