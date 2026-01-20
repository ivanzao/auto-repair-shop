package br.com.soat.auth.port

import br.com.soat.auth.model.RefreshToken
import java.util.UUID

interface RefreshTokenRepository {
    fun findByToken(token: UUID): RefreshToken?
    fun save(refreshToken: RefreshToken): RefreshToken
    fun delete(token: UUID)
}
