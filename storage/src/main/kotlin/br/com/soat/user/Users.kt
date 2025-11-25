package br.com.soat.user

import br.com.soat.user.model.User
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Users: Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val contact = varchar("contact", 255)
    val document = varchar("document", 255)
    val role = varchar("role", 255)

    init {
        PrimaryKey(id)
        uniqueIndex(document)
    }
}

fun ResultRow.toUser(): User = User(
    id = this[Users.id],
    createdAt = this[Users.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Users.modifiedAt].toJavaLocalDateTime(),
    version = this[Users.version],
    name = this[Users.name],
    document = this[Users.document],
    email = this[Users.email],
    contact = this[Users.contact],
    role = User.Role.valueOf(this[Users.role])
)