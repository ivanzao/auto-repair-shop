package br.com.soat.consumer

import br.com.soat.bus.EventBus
import br.com.soat.event.EventProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class EventConsumerWorker(
    private val eventBus: EventBus,
    private val eventProcessor: EventProcessor,
    private val dispatcher: CoroutineDispatcher
) {
    private val logger = LoggerFactory.getLogger(EventConsumerWorker::class.java)

    fun start() {
        logger.info("Starting EventConsumerWorker")
        CoroutineScope(dispatcher).launch {
            eventBus.consume { event ->
                logger.info("EventConsumerWorker processing event ${event::class.simpleName} with id ${event.id}")
                eventProcessor.process(event)
            }
        }
    }
}
