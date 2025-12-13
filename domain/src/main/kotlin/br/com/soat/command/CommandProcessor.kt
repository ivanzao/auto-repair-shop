package br.com.soat.command

import br.com.soat.command.model.Command
import br.com.soat.command.model.CommandStatus
import br.com.soat.command.repository.CommandRepository
import org.slf4j.LoggerFactory

class CommandProcessor(
    private val commandRepository: CommandRepository,
    handlers: List<CommandHandler>
) {

    private val logger = LoggerFactory.getLogger(CommandProcessor::class.java)

    private val handlerMap: Map<String, CommandHandler> = handlers.associateBy { it.commandType.qualifiedName!! }

    fun processCommands() {
        commandRepository.findPendingCommands(10).forEach {
            command -> processCommand(command)
        }
    }

    fun processCommand(command: Command) {
        try {
            val commandTypeKey = command::class.qualifiedName!!
            val handler = handlerMap[commandTypeKey]

            if (handler == null) {
                logger.warn("No handler found for command type: ${command::class.simpleName}")
                commandRepository.updateStatus(command.id, CommandStatus.FAILED)
                return
            }

            handler.handle(command)
            commandRepository.updateStatus(command.id, CommandStatus.PROCESSED)
        } catch (e: Exception) {
            logger.error("Failed to process command ${command.id}", e)
            commandRepository.updateStatus(command.id, CommandStatus.FAILED)
        }
    }
}
