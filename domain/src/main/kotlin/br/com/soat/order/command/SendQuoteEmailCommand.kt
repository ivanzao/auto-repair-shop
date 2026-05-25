package br.com.soat.order.command

import br.com.soat.command.model.Command
import br.com.soat.command.model.CommandStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class SendQuoteEmailCommand(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val status: CommandStatus = CommandStatus.PENDING,

    val orderId: UUID,
    val callbackToken: String,
    val customerEmail: String,
    val customerName: String,
    val totalAmount: BigDecimal,
    val services: List<Service>,
    val supplies: List<Supply>,
) : Command {

    data class Service(val name: String, val price: BigDecimal)
    data class Supply(val name: String, val quantity: Int, val unitPrice: BigDecimal)
}
