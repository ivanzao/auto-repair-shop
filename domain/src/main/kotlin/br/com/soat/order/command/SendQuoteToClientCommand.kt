package br.com.soat.order.command

import br.com.soat.command.model.Command
import br.com.soat.command.model.CommandStatus
import java.time.LocalDateTime
import java.util.UUID

data class SendQuoteToClientCommand(
    override val id: UUID = UUID.randomUUID(),
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val status: CommandStatus = CommandStatus.PENDING,
    
    val orderId: UUID,
    val idempotencyId: UUID
) : Command
