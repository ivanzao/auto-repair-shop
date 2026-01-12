package br.com.soat.bus

import br.com.soat.command.model.Command
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory

class CommandBus {
    private val logger = LoggerFactory.getLogger(CommandBus::class.java)
    private val channel = Channel<Command>(Channel.UNLIMITED)

    suspend fun publish(command: Command) {
        logger.debug("Enqueuing command ${command::class.simpleName} with id ${command.id}")
        channel.send(command)
    }

    suspend fun consume(handler: suspend (Command) -> Unit) {
        for (command in channel) {
            try {
                logger.debug("Consuming command ${command::class.simpleName} with id ${command.id}")
                handler(command)
            } catch (e: Exception) {
                logger.error("Error consuming command ${command.id}", e)
            }
        }
    }
}
