package br.com.soat.customer

import br.com.soat.customer.model.Customer
import br.com.soat.shared.document
import br.com.soat.shared.email
import br.com.soat.shared.phoneNumber
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Customers: Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val name = varchar("name", 255)
    val document = document("document")
    val email = email("email")
    val contact = phoneNumber("contact")
}

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[Customers.id],
    createdAt = this[Customers.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Customers.modifiedAt].toJavaLocalDateTime(),
    version = this[Customers.version],
    name = this[Customers.name],
    document = this[Customers.document],
    email = this[Customers.email],
    contact = this[Customers.contact],
)

