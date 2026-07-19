package br.com.soat.event

import org.slf4j.LoggerFactory

/**
 * Despacha um [EventEnvelope] recebido de uma fila para os [InboundEventHandler]s
 * cujo `eventType` casa, com deduplicação por `(eventId, consumerId)`. Como cada
 * fila recebe um superset dos eventos, um `eventType` sem handler é apenas
 * ignorado e considerado processado.
 */
class InboundEventDispatcher(
    private val processedStore: ProcessedEventStore,
    handlers: List<InboundEventHandler>,
) {
    private val logger = LoggerFactory.getLogger(InboundEventDispatcher::class.java)
    private val handlerMap: Map<String, List<InboundEventHandler>> =
        handlers.flatMap { handler -> handler.eventTypes.map { it to handler } }
            .groupBy({ it.first }, { it.second })

    /**
     * @return `true` se o envelope foi totalmente processado (pode ser removido
     * da fila); `false` se algum handler falhou (mensagem volta para a fila → DLQ).
     */
    fun process(envelope: EventEnvelope): Boolean {
        val matched = handlerMap[envelope.eventType].orEmpty()
        if (matched.isEmpty()) {
            logger.info(
                "No handler for eventType={} (eventId={}); marking as processed",
                envelope.eventType, envelope.eventId,
            )
            return true
        }

        var allOk = true
        matched.forEach { handler ->
            if (processedStore.isProcessed(envelope.eventId, handler.consumerId)) {
                logger.debug("Skipping already-processed event {} for {}", envelope.eventId, handler.consumerId)
                return@forEach
            }
            try {
                handler.handle(envelope)
                processedStore.markAsProcessed(envelope.eventId, handler.consumerId)
            } catch (e: Exception) {
                logger.error("Handler {} failed for event {}", handler.consumerId, envelope.eventId, e)
                allOk = false
            }
        }
        return allOk
    }
}
