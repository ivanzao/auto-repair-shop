package br.com.soat.shared

import java.time.LocalDateTime
import java.util.UUID

interface Command {
    val id: UUID
    val createdAt: LocalDateTime
    val status: CommandStatus
}
