import br.com.soat.shared.DomainEvent
import br.com.soat.shared.EventStatus
import br.com.soat.shared.repository.EventRepository
import br.com.soat.worker.event.EventHandler
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class EventDispatcherWorker(
    private val eventRepository: EventRepository,
    handlers: List<EventHandler>,
    private val dispatcher: CoroutineDispatcher
) {

    private val logger = LoggerFactory.getLogger(EventDispatcherWorker::class.java)
    private var scope: CoroutineScope? = null
    private var isRunning = false
    private var job: Job? = null

    private val handlerMap: Map<KClass<out DomainEvent>, List<EventHandler>> =
        handlers.groupBy { it.eventType }

    fun start() {
        if (isRunning) return
        isRunning = true

        scope = CoroutineScope(dispatcher).apply {
            job = launch {
                logger.info("Starting EventDispatcherWorker...")

                while (isRunning) {
                    try {
                        val pendingEvents = eventRepository.findPendingEvents(limit = 10)
                        logger.info("Found ${pendingEvents.size} pending events")

                        if (pendingEvents.isEmpty()) {
                            delay(300)
                            continue
                        }

                        pendingEvents.forEach { event ->
                            processEvent(event)
                        }

                    } catch (e: Exception) {
                        logger.error("Error in EventDispatcherWorker loop", e)
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun processEvent(event: DomainEvent) {
        try {
            val handlers = handlerMap[event::class]

            if (handlers.isNullOrEmpty()) {
                logger.warn("No handler found for event type: ${event::class.simpleName}")
                eventRepository.updateStatus(event.id, EventStatus.PROCESSED)
                return
            }

            var allHandlersSuccess = true

            handlers.forEach { handler ->
                val consumerId = handler::class.simpleName ?: "UnknownHandler"

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

    fun stop() {
        isRunning = false
        runBlocking {  job?.join() }
        scope?.cancel()
    }
}
