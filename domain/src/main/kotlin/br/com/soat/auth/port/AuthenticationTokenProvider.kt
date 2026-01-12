package br.com.soat.auth.port

import br.com.soat.auth.model.AuthenticationTokenValidationResult
import br.com.soat.user.model.User
import java.time.LocalDateTime

interface AuthenticationTokenProvider {
    fun generate(user: User, expiresAt: LocalDateTime): String
    fun validate(token: String): AuthenticationTokenValidationResult
}
