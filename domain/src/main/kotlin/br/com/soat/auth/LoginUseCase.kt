package br.com.soat.auth

import br.com.soat.auth.model.AuthenticateRequest
import br.com.soat.auth.model.AuthenticateResponse
import br.com.soat.auth.model.RefreshToken
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.auth.port.RefreshTokenRepository
import br.com.soat.config.Config
import br.com.soat.security.HashService
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.user.UserRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

class LoginUseCase(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authenticationTokenProvider: AuthenticationTokenProvider,
    private val tx: RepositoryTransactionHandler,
    private val hashService: HashService,
    private val clock: Clock,
    config: Config,
) {

    private val authenticationExpiresIn = config.getInt("security.authentication.expiresIn")

    fun login(request: AuthenticateRequest): AuthenticateResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalStateException("User not found")

        if (!hashService.check(request.password, user.hashedPassword))
            throw IllegalStateException("Invalid password")

        val tokenExpiresAt = getTokenExpiresAt()
        val accessToken = authenticationTokenProvider.generate(user, tokenExpiresAt)

        val refreshToken = refreshTokenRepository.save(RefreshToken(user = user))

        return AuthenticateResponse(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
            expiresAt = tokenExpiresAt,
        )
    }

    fun refresh(refreshToken: UUID): AuthenticateResponse {
        val currentRefreshToken = refreshTokenRepository.findByToken(refreshToken)
            ?: throw IllegalStateException("RefreshToken not found")

        val tokenExpiresAt = getTokenExpiresAt()
        val accessToken = authenticationTokenProvider.generate(currentRefreshToken.user, tokenExpiresAt)

        val newRefreshToken = tx.inTransaction {
            refreshTokenRepository.delete(currentRefreshToken.token)
            refreshTokenRepository.save(RefreshToken(user = currentRefreshToken.user))
        }

        return AuthenticateResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken.token,
            expiresAt = tokenExpiresAt,
        )
    }

    private fun getTokenExpiresAt() =
        LocalDateTime.now(clock).plusMinutes(authenticationExpiresIn.toLong())
}