package br.com.soat.publisher

import br.com.soat.bus.EventBus
import br.com.soat.event.EventPublisher
import br.com.soat.event.model.DomainEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class DefaultEventPublisher(
    private val dispatcher: CoroutineDispatcher,
    private val eventBus: EventBus
) : EventPublisher {

    private val logger = LoggerFactory.getLogger(DefaultEventPublisher::class.java)

    override fun publish(event: DomainEvent) {
        try {
            logger.info("Publishing event ${event::class.simpleName} with id ${event.id}")
            CoroutineScope(dispatcher).launch {
                eventBus.publish(event)
            }
        } catch (e: Exception) {
            logger.warn("Failed to publish event ${event.id} immediately. Will be processed by scheduler.", e)
        }
    }
}
