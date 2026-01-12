package br.com.soat.order.model

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class OrderApprovalToken(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val version: Int = 0,
    val orderId: UUID,
    val expiresAt: LocalDateTime,
    val usedAt: LocalDateTime? = null
) {
    fun isValid(): Boolean = usedAt == null && now().isBefore(expiresAt)
    fun markAsUsed(): OrderApprovalToken = copy(usedAt = now())
}