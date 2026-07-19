package br.com.soat.messaging

import br.com.soat.event.OutboxRepository
import br.com.soat.event.toEnvelopeJson
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Lê os eventos pendentes do outbox e publica cada um como envelope na SNS,
 * marcando como publicado em seguida. O `traceparent` (propagação de trace) é
 * fornecido por [traceparentProvider] quando houver um span ativo.
 */
class OutboxRelay(
    private val outbox: OutboxRepository,
    private val sns: SnsClient,
    private val mapper: ObjectMapper,
    private val traceparentProvider: () -> String? = { null },
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    fun relayPending(batch: Int = 10) {
        val pending = outbox.findPending(batch)
        if (pending.isEmpty()) return

        pending.forEach { event ->
            try {
                sns.publish(
                    payload = event.toEnvelopeJson(mapper),
                    eventType = event.eventType,
                    messageId = event.eventId.toString(),
                    traceparent = traceparentProvider(),
                )
                outbox.markPublished(event.eventId)
                logger.info("Relayed outbox event {} ({}) to SNS", event.eventId, event.eventType)
            } catch (e: Exception) {
                logger.error("Failed to relay outbox event {} ({})", event.eventId, event.eventType, e)
            }
        }
    }
}
