package br.com.soat.order.command.handler

import br.com.soat.command.handler.CommandHandler
import br.com.soat.command.model.Command
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.command.SendQuoteToClientCommand
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class SendQuoteToClientCommandHandler(
    private val orderListenerUseCase: OrderListenerUseCase,
) : CommandHandler {

    private val logger = LoggerFactory.getLogger(SendQuoteToClientCommandHandler::class.java)

    override val commandType: KClass<out Command> = SendQuoteToClientCommand::class

    override fun handle(command: Command) {
        if (command !is SendQuoteToClientCommand) return
        logger.info("Processing SendQuoteToClientCommand for Order ${command.orderId}")
        orderListenerUseCase.sendQuoteApprovalEmail(command.orderId)
    }
}