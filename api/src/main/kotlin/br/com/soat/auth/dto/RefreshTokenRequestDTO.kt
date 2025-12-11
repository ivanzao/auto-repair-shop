package br.com.soat.auth.dto

import java.util.UUID

data class RefreshTokenRequestDTO(
    val refreshToken: UUID
)