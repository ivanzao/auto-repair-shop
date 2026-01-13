package br.com.soat.user

import br.com.soat.shared.document
import br.com.soat.shared.email
import br.com.soat.shared.phoneNumber
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
    val hashedPassword = varchar("hashed_password", 255)
    val email = email("email")
    val contact = phoneNumber("contact")
    val document = document("document")
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
    hashedPassword = this[Users.hashedPassword],
    document = this[Users.document],
    email = this[Users.email],
    contact = this[Users.contact],
    role = User.Role.valueOf(this[Users.role])
)