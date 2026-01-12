package br.com.soat.command

import br.com.soat.command.model.Command
import br.com.soat.command.model.CommandStatus
import br.com.soat.command.repository.CommandRepository
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime.now
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class CommandPostgresRepository : CommandRepository {

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    override fun save(command: Command): Command = transaction {
        Commands.insert {
            it[id] = command.id
            it[createdAt] = command.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = command.createdAt.toKotlinLocalDateTime()
            it[status] = command.status.name
            it[type] = command::class.qualifiedName ?: "Unknown"
            it[payload] = objectMapper.writeValueAsString(command)
        }
        command
    }

    override fun findPendingCommands(limit: Int): List<Command> = transaction {
        Commands.selectAll()
            .where { Commands.status eq CommandStatus.PENDING.name }
            .orderBy(Commands.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                val typeName = row[Commands.type]
                val payload = row[Commands.payload]
                val clazz = Class.forName(typeName).kotlin
                objectMapper.readValue(payload, clazz.java) as Command
            }
    }

    override fun updateStatus(id: UUID, status: CommandStatus) {
        transaction {
            Commands.update({ Commands.id eq id }) {
                it[Commands.status] = status.name
                it[Commands.modifiedAt] = now().toKotlinLocalDateTime()
            }
        }
    }
}