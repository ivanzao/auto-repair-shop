package br.com.soat.customer

import br.com.soat.customer.model.Customer
import java.util.UUID

interface CustomerRepository {
    fun findById(id: UUID): Customer?
    fun create(customer: Customer): Customer
}