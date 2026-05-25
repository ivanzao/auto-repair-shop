package br.com.soat.messaging

import br.com.soat.command.handler.CommandHandler
import br.com.soat.command.model.Command
import br.com.soat.order.command.SendQuoteEmailCommand
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

class SnsRelayCommandHandler(
    private val sns: SnsClient,
    private val mapper: ObjectMapper,
) : CommandHandler {

    private val logger = LoggerFactory.getLogger(SnsRelayCommandHandler::class.java)

    override val commandType: KClass<out Command> = SendQuoteEmailCommand::class

    override fun handle(command: Command) {
        if (command !is SendQuoteEmailCommand) return

        val payload = mapper.writeValueAsString(command)
        sns.publish(
            payload = payload,
            eventType = "SendQuoteEmailCommand",
            messageId = command.id.toString(),
        )
        logger.info("Published command ${command::class.simpleName}/${command.id} to SNS")
    }
}
