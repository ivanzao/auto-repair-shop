package br.com.soat.bus

import br.com.soat.event.model.DomainEvent
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory

class EventBus {
    private val logger = LoggerFactory.getLogger(EventBus::class.java)
    private val channel = Channel<DomainEvent>(Channel.UNLIMITED)

    suspend fun publish(event: DomainEvent) {
        logger.info("Enqueuing event ${event::class.simpleName} with id ${event.id}")
        channel.send(event)
    }

    suspend fun consume(handler: suspend (DomainEvent) -> Unit) {
        for (event in channel) {
            try {
                logger.info("Consuming event ${event::class.simpleName} with id ${event.id}")
                handler(event)
            } catch (e: Exception) {
                logger.error("Error consuming event ${event.id}", e)
            }
        }
    }
}
