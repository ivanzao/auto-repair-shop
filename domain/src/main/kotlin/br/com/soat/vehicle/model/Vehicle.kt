package br.com.soat.vehicle.model

import java.time.LocalDateTime
import java.util.UUID

data class Vehicle(
    val id: UUID = UUID.randomUUID(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
    val version: Int = 0,

    val clientId: UUID,

    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
)