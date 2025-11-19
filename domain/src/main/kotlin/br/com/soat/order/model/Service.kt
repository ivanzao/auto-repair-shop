package br.com.soat.br.com.soat.order.model

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class Service(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val name: String,
    val description: String?,
    val price: Double,
)