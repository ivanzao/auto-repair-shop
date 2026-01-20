package br.com.soat.auth.dto

import br.com.soat.auth.model.AuthenticateResponse
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.UUID

data class AuthenticateUserResponseDTO(
    val accessToken: String,
    val refreshToken: UUID,
    val expiresAt: ZonedDateTime
) {
    companion object {
        fun from(response: AuthenticateResponse) = AuthenticateUserResponseDTO(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAt = response.expiresAt.atZone(UTC)
        )
    }
}