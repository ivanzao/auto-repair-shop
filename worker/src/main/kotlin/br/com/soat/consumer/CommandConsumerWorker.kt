package br.com.soat.consumer

import br.com.soat.bus.CommandBus
import br.com.soat.command.CommandProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class CommandConsumerWorker(
    private val commandBus: CommandBus,
    private val commandProcessor: CommandProcessor,
    private val dispatcher: CoroutineDispatcher
) {
    private val logger = LoggerFactory.getLogger(CommandConsumerWorker::class.java)

    fun start() {
        logger.info("Starting CommandConsumerWorker")
        CoroutineScope(dispatcher).launch {
            commandBus.consume { command ->
                logger.info("CommandConsumerWorker processing command ${command::class.simpleName} with id ${command.id}")
                commandProcessor.process(command)
            }
        }
    }
}
