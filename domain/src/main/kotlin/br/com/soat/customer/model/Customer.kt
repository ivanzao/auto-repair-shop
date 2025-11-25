package br.com.soat.customer.model

import java.time.LocalDateTime
import java.util.UUID

data class Customer(
    val id: UUID = UUID.randomUUID(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
    val version: Int = 0,

    val name: String,
    val document: String,
    val email: String,
    val contact: String,
)