package br.com.soat.br.com.soat.customer

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class Customer(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),

    val name: String,
    val document: String,
    val email: String,
    val contact: String,
)