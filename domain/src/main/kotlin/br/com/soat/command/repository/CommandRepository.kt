package br.com.soat.command.repository

import br.com.soat.command.model.Command
import br.com.soat.command.model.CommandStatus
import java.util.UUID

interface CommandRepository {
    fun findPendingCommands(limit: Int): List<Command>
    fun save(command: Command): Command
    fun updateStatus(id: UUID, status: CommandStatus)
}
