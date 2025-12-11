package br.com.soat.command.model

import java.time.LocalDateTime
import java.util.UUID

interface Command {
    val id: UUID
    val createdAt: LocalDateTime
    val status: CommandStatus
}
