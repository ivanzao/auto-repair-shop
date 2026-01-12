package br.com.soat.publisher

import br.com.soat.bus.CommandBus
import br.com.soat.command.CommandPublisher
import br.com.soat.command.model.Command
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class DefaultCommandPublisher(
    private val dispatcher: CoroutineDispatcher,
    private val commandBus: CommandBus
) : CommandPublisher {

    private val logger = LoggerFactory.getLogger(DefaultCommandPublisher::class.java)

    override fun publish(command: Command) {
        try {
            logger.info("Publishing command ${command::class.simpleName} with id ${command.id}")
            CoroutineScope(dispatcher).launch {
                commandBus.publish(command)
            }
        } catch (e: Exception) {
            logger.warn("Failed to publish command ${command.id} immediately. Will be processed by scheduler.", e)
        }
    }
}
