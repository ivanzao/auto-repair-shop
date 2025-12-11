package br.com.soat.auth

import br.com.soat.auth.model.RefreshToken
import br.com.soat.auth.port.RefreshTokenRepository
import br.com.soat.user.Users
import br.com.soat.user.toUser
import java.util.UUID
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class RefreshTokenPostgresRepository : RefreshTokenRepository {

    override fun save(refreshToken: RefreshToken): RefreshToken = transaction {
        RefreshTokens.insert {
            it[token] = refreshToken.token
            it[createdAt] = refreshToken.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = refreshToken.modifiedAt.toKotlinLocalDateTime()
            it[version] = refreshToken.version
            it[userId] = refreshToken.user.id
        }.resultedValues?.singleOrNull()
            ?.let { row ->
                Users.selectAll()
                    .where { Users.id eq row[RefreshTokens.userId] }
                    .first()
                    .let { userRow ->
                        RefreshToken(
                            token = row[RefreshTokens.token],
                            createdAt = row[RefreshTokens.createdAt].toJavaLocalDateTime(),
                            modifiedAt = row[RefreshTokens.modifiedAt].toJavaLocalDateTime(),
                            version = row[RefreshTokens.version],
                            user = userRow.toUser()
                        )
                    }
            }
            ?: throw IllegalStateException("An error occurred while saving RefreshToken")
    }

    override fun delete(token: UUID): RefreshToken = transaction {
        val refreshToken = findByToken(token)
            ?: throw IllegalStateException("RefreshToken not found")

        RefreshTokens.deleteWhere { RefreshTokens.token eq token }

        refreshToken
    }

    override fun findByToken(refreshToken: UUID): RefreshToken? = transaction {
        RefreshTokens.innerJoin(Users)
            .selectAll()
            .where { RefreshTokens.token eq refreshToken }
            .limit(1)
            .firstOrNull()
            ?.toRefreshToken()
    }
}