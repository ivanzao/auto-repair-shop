package br.com.soat.shared

import java.time.LocalDateTime
import java.util.UUID

interface DomainEvent {
    val id: UUID
    val createdAt: LocalDateTime
    val modifiedAt: LocalDateTime
    val version: Int
}