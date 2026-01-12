package br.com.soat.auth.model

import java.util.UUID

data class AuthenticationTokenValidationResult(
    val userId: UUID?,
    val role: String?,
    val isValid: Boolean
)