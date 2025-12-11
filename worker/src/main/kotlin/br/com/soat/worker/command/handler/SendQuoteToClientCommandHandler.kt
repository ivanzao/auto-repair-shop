package br.com.soat.worker.command.handler

import br.com.soat.command.CommandHandler
import br.com.soat.order.command.SendQuoteToClientCommand
import br.com.soat.shared.Command
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class SendQuoteToClientCommandHandler : CommandHandler {

    private val logger = LoggerFactory.getLogger(SendQuoteToClientCommandHandler::class.java)

    override val commandType: KClass<out Command> = SendQuoteToClientCommand::class

    override fun handle(command: Command) {
        if (command !is SendQuoteToClientCommand) return

        logger.info("Processing SendQuoteToClientCommand for Order ${command.orderId}")
        logger.info("Sending quote to client for Order ${command.orderId} (MOCKED)")
    }
}