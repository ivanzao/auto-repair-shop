package br.com.soat.customer.repository

import br.com.soat.customer.model.Customer
import java.util.UUID

interface CustomerRepository {
    fun findById(id: UUID): Customer?
    fun findAll(): List<Customer>
    fun create(customer: Customer): Customer
    fun update(customer: Customer): Customer
    fun delete(id: UUID)
}
