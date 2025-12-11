package br.com.soat.auth

import br.com.soat.auth.model.RefreshToken
import br.com.soat.user.Users
import br.com.soat.user.toUser
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object RefreshTokens : Table("refresh_tokens") {
    val token = uuid("token")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val userId = uuid("user_id").references(Users.id)

    override val primaryKey = PrimaryKey(token)
}

fun ResultRow.toRefreshToken(): RefreshToken = RefreshToken(
    token = this[RefreshTokens.token],
    createdAt = this[RefreshTokens.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[RefreshTokens.modifiedAt].toJavaLocalDateTime(),
    version = this[RefreshTokens.version],
    user = this.toUser()
)