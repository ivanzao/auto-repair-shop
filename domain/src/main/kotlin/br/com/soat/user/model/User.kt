package br.com.soat.user.model

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class User(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val version: Int = 0,

    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val role: Role,
) {

    enum class Role {
        ADMIN, ATTENDANT
    }
}