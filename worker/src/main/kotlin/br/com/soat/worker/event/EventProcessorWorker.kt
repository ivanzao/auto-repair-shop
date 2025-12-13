package br.com.soat.worker.event

import br.com.soat.event.EventProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class EventProcessorWorker(
    private val eventProcessor: EventProcessor,
    private val dispatcher: CoroutineDispatcher
) {

    private val logger = LoggerFactory.getLogger(EventProcessorWorker::class.java)

    private var scope: CoroutineScope? = null
    private var isRunning = false
    private var job: Job? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        scope = CoroutineScope(dispatcher).apply {
            job = launch {
                logger.info("EventProcessorWorker started")
                while (isRunning) {
                    try {
                        eventProcessor.processEvents()
                        delay(1000)
                    } catch (e: Exception) {
                        logger.error("Error in EventProcessorWorker loop", e)
                        delay(5000)
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        runBlocking { job?.join() }
        scope?.cancel()
    }
}
