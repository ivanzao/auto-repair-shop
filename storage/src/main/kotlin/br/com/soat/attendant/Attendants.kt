package br.com.soat.attendant

import br.com.soat.attendant.model.Attendant
import br.com.soat.shared.document
import br.com.soat.shared.email
import br.com.soat.shared.phoneNumber
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Attendants : Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val name = varchar("name", 255)
    val document = document("document")
    val email = email("email")
    val contact = phoneNumber("contact")

    init {
        PrimaryKey(id)
        uniqueIndex(document)
        uniqueIndex(email)
    }
}

fun ResultRow.toAttendant(): Attendant = Attendant(
    id = this[Attendants.id],
    createdAt = this[Attendants.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Attendants.modifiedAt].toJavaLocalDateTime(),
    version = this[Attendants.version],
    name = this[Attendants.name],
    document = this[Attendants.document],
    email = this[Attendants.email],
    contact = this[Attendants.contact],
)
