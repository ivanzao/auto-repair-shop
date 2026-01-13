package br.com.soat.customer.model

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import java.time.LocalDateTime
import java.util.UUID

data class Customer(
    val id: UUID = UUID.randomUUID(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
    val version: Int = 0,

    val name: String,
    val document: Document,
    val email: Email,
    val contact: PhoneNumber,
)