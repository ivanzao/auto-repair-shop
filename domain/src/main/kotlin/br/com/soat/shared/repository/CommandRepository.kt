package br.com.soat.shared.repository

import br.com.soat.shared.Command
import br.com.soat.shared.CommandStatus
import java.util.UUID

interface CommandRepository {
    fun save(command: Command): Command
    fun findPendingCommands(limit: Int): List<Command>
    fun updateStatus(id: UUID, status: CommandStatus)
}
