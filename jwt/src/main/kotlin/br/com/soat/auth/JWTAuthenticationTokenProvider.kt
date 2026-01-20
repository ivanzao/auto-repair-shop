package br.com.soat.auth

import br.com.soat.auth.model.AuthenticationTokenValidationResult
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.config.Config
import br.com.soat.user.model.User
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.UUID

class JWTAuthenticationTokenProvider(
    private val config: Config,
    private val clock: Clock,
) : AuthenticationTokenProvider {

    private val verifier = JWT.require(Algorithm.HMAC512(config.getString("security.jwt.secret"))).build()

    override fun generate(
        user: User,
        expiresAt: LocalDateTime
    ): String = JWT.create()
        .withIssuer("https://auto-repair-shop")
        .withAudience("https://my-api/secure")
        .withSubject(user.id.toString())
        .withClaim("role", user.role.name)
        .withIssuedAt(clock.instant())
        .withExpiresAt(expiresAt.toInstant(UTC))
        .withJWTId(UUID.randomUUID().toString())
        .sign(Algorithm.HMAC512(config.getString("security.jwt.secret")))

    override fun validate(token: String) = try {
        val decodedJWT = verifier.verify(token)
        AuthenticationTokenValidationResult(
            userId = UUID.fromString(decodedJWT.subject),
            role = decodedJWT.getClaim("role").asString(),
            isValid = true
        )
    } catch (_: Exception) {
        AuthenticationTokenValidationResult(null, null, false)
    }
}