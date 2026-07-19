package br.com.soat.event

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Serializa um [OutboxEvent] no envelope do contrato compartilhado:
 * `{ eventId, eventType, eventVersion, occurredAt, payload }`.
 */
fun OutboxEvent.toEnvelopeJson(mapper: ObjectMapper): String {
    val node = mapper.createObjectNode().apply {
        put("eventId", eventId.toString())
        put("eventType", eventType)
        put("eventVersion", eventVersion)
        put("occurredAt", occurredAt.toString())
        set<JsonNode>("payload", mapper.readTree(payload))
    }
    return mapper.writeValueAsString(node)
}
