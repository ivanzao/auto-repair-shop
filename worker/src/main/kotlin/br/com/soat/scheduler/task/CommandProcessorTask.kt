package br.com.soat.scheduler.task

import br.com.soat.command.CommandProcessor
import br.com.soat.scheduler.ScheduledTask
import org.slf4j.LoggerFactory

class CommandProcessorTask(
    private val commandProcessor: CommandProcessor,
): ScheduledTask {

    private val logger = LoggerFactory.getLogger(CommandProcessorTask::class.java)

    override fun execute() {
        logger.debug("Processing pending commands")
        commandProcessor.processPendingCommands()
    }

    override fun getLockName(): String = "process-pending-commands"

    override fun getIntervalInSeconds(): Long = 5
}
