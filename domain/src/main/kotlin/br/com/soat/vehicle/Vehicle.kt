package br.com.soat.br.com.soat.vehicle

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class Vehicle(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val clientId: UUID,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
)