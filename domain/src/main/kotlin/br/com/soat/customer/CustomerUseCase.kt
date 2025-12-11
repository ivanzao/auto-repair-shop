package br.com.soat.customer

import br.com.soat.customer.model.CreateCustomerRequest
import br.com.soat.customer.model.Customer
import java.util.UUID

class CustomerUseCase(
    private val repository: CustomerRepository
) {

    fun findById(id: UUID) = repository.findById(id)
    fun findAll(): List<Customer> = repository.findAll()

    fun create(request: CreateCustomerRequest): Customer =
        repository.create(
            Customer(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact
            )
        )

    fun update(id: UUID, request: CreateCustomerRequest): Customer {
        val existingCustomer = repository.findById(id)
            ?: throw IllegalStateException("Trying to update a non-existing customer $id")

        return repository.update(
            existingCustomer.copy(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact,
                modifiedAt = java.time.LocalDateTime.now()
            )
        )
    }

    fun delete(id: UUID): Boolean = repository.delete(id)
}