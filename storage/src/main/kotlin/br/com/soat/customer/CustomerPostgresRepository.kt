package br.com.soat.customer

import br.com.soat.customer.model.Customer
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class CustomerPostgresRepository : CustomerRepository {

    override fun findById(id: UUID) = transaction {
        Customers.selectAll()
            .where { Customers.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toCustomer()
    }

    override fun create(customer: Customer) = transaction {
        Customers.insert {
            it[id] = customer.id
            it[createdAt] = customer.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = customer.modifiedAt.toKotlinLocalDateTime()
            it[version] = customer.version
            it[Customers.name] = customer.name
            it[Customers.document] = customer.document
            it[Customers.email] = customer.email
            it[Customers.contact] = customer.contact
        }.resultedValues?.singleOrNull()?.toCustomer()
            ?: throw IllegalStateException("An error occurred while saving Customer")
    }
}