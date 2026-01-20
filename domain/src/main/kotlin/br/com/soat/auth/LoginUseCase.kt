package br.com.soat.auth

import br.com.soat.auth.exception.InvalidLoginCredentialsException
import br.com.soat.auth.exception.InvalidRefreshTokenException
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
            ?: throw InvalidLoginCredentialsException()

        if (!hashService.check(request.password, user.hashedPassword))
            throw InvalidLoginCredentialsException()

        val tokenExpiresAt = getTokenExpiresAt()
        val accessToken = authenticationTokenProvider.generate(user, tokenExpiresAt)
        val refreshToken = refreshTokenRepository.save(RefreshToken(user = user))

        return AuthenticateResponse(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
            expiresAt = tokenExpiresAt,
        )
    }

    fun refresh(token: UUID): AuthenticateResponse {
        val refreshToken = refreshTokenRepository.findByToken(token) ?: throw InvalidRefreshTokenException()

        val tokenExpiresAt = getTokenExpiresAt()
        val accessToken = authenticationTokenProvider.generate(refreshToken.user, tokenExpiresAt)

        val newRefreshToken = tx.inTransaction {
            refreshTokenRepository.delete(refreshToken.token)
            refreshTokenRepository.save(RefreshToken(user = refreshToken.user))
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