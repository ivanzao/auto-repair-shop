package br.com.soat.auth.port

import br.com.soat.auth.model.RefreshToken
import java.util.UUID

interface RefreshTokenRepository {
    fun save(refreshToken: RefreshToken): RefreshToken
    fun delete(token: UUID): RefreshToken
    fun findByToken(refreshToken: UUID): RefreshToken?
}