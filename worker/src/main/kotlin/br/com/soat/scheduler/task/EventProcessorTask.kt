package br.com.soat.scheduler.task

import br.com.soat.event.EventProcessor
import br.com.soat.scheduler.ScheduledTask
import org.slf4j.LoggerFactory

class EventProcessorTask(
    private val eventProcessor: EventProcessor
) : ScheduledTask {

    private val logger = LoggerFactory.getLogger(EventProcessorTask::class.java)

    override fun execute() {
        logger.debug("Processing pending events")
        eventProcessor.processPendingEvents()
    }

    override fun getLockName(): String = "process-pending-events"

    override fun getIntervalInSeconds(): Long = 5
}
