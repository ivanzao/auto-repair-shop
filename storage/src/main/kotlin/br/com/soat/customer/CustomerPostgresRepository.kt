package br.com.soat.customer

import br.com.soat.customer.model.Customer
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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

    override fun findAll(): List<Customer> = transaction {
        Customers.selectAll().map { it.toCustomer() }
    }

    override fun update(customer: Customer): Customer = transaction {
        Customers.update({ (Customers.id eq customer.id) and (Customers.version eq customer.version) }) {
            it[modifiedAt] = customer.modifiedAt.toKotlinLocalDateTime()
            it[version] = customer.version + 1

            it[name] = customer.name
            it[document] = customer.document
            it[email] = customer.email
            it[contact] = customer.contact
        }

        findById(customer.id) ?: throw IllegalStateException("Customer not found after update")
    }

    override fun delete(id: UUID) = transaction {
        Customers.deleteWhere { Customers.id eq id }
    } == 1
}
