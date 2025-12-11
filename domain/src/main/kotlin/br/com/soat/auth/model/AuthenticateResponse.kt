package br.com.soat.auth.model

import java.time.LocalDateTime
import java.util.UUID

data class AuthenticateResponse(
    val accessToken: String,
    val refreshToken: UUID,
    val expiresAt: LocalDateTime,
)
