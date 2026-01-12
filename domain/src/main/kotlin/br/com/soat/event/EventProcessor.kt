package br.com.soat.event

import br.com.soat.event.model.DomainEvent
import br.com.soat.event.model.EventStatus
import br.com.soat.event.repository.EventRepository
import org.slf4j.LoggerFactory

class EventProcessor(
    private val eventRepository: EventRepository,
    handlers: List<EventHandler>
) {

    private val logger = LoggerFactory.getLogger(EventProcessor::class.java)

    private val handlerMap: Map<String, List<EventHandler>> = handlers.groupBy { it.eventType.qualifiedName!! }

    fun processPendingEvents() {
        eventRepository.findPendingEvents(10).forEach {
            event -> process(event)
        }
    }

    fun process(event: DomainEvent) {
        try {
            val eventTypeKey = event::class.qualifiedName!!
            val eventHandlers = handlerMap[eventTypeKey]

            if (eventHandlers.isNullOrEmpty()) {
                logger.warn("No handler found for event type: ${event::class.simpleName}")
                eventRepository.updateStatus(event.id, EventStatus.PROCESSED)
                return
            }

            var allHandlersSuccess = true

            eventHandlers.forEach { handler ->
                val consumerId = handler::class.simpleName!!

                if (!eventRepository.isProcessed(event.id, consumerId)) {
                    try {
                        handler.handle(event)
                        eventRepository.markAsProcessed(event.id, consumerId)
                    } catch (e: Exception) {
                        logger.error("Handler $consumerId failed for event ${event.id}", e)
                        allHandlersSuccess = false
                    }
                }
            }

            if (allHandlersSuccess) {
                eventRepository.updateStatus(event.id, EventStatus.PROCESSED)
            }
        } catch (e: Exception) {
            logger.error("Failed to process event ${event.id}", e)
            eventRepository.updateStatus(event.id, EventStatus.FAILED)
        }
    }
}
