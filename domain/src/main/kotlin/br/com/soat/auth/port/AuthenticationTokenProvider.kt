package br.com.soat.auth.port

import br.com.soat.user.model.User
import java.time.LocalDateTime
import java.util.UUID

interface AuthenticationTokenProvider {
    fun generate(user: User, expiresAt: LocalDateTime): String
    fun validate(token: String): TokenValidationResult
}

data class TokenValidationResult(
    val userId: UUID?,
    val role: String?,
    val isValid: Boolean
)