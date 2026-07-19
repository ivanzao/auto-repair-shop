package br.com.soat.consumer

import br.com.soat.event.EventEnvelope
import br.com.soat.event.InboundEventDispatcher
import br.com.soat.messaging.MessageQueue
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Consome envelopes da fila de entrada, despacha por `eventType` e remove a
 * mensagem apenas no sucesso. Em falha (parse ou handler), a mensagem não é
 * removida: volta para a fila após o visibility timeout e, após 3 tentativas,
 * cai na DLQ.
 */
class InboundEventConsumer(
    private val queue: MessageQueue,
    private val dispatcher: InboundEventDispatcher,
    private val mapper: ObjectMapper,
    private val coroutineDispatcher: CoroutineDispatcher,
) {
    private val logger = LoggerFactory.getLogger(InboundEventConsumer::class.java)

    fun start() {
        logger.info("Starting InboundEventConsumer")
        val scope = CoroutineScope(coroutineDispatcher)
        scope.launch {
            while (isActive) {
                pollOnce()
            }
        }
    }

    /** Processa um ciclo de long-poll. Exposto para teste. */
    fun pollOnce() {
        val messages = queue.receive(maxMessages = 10, waitSeconds = 20)
        for (message in messages) {
            val envelope = try {
                mapper.readValue(message.body, EventEnvelope::class.java)
            } catch (e: Exception) {
                logger.error("Failed to parse envelope, leaving message for redrive: {}", message.body, e)
                continue
            }
            if (dispatcher.process(envelope)) {
                queue.delete(message.receiptHandle)
            }
        }
    }
}
