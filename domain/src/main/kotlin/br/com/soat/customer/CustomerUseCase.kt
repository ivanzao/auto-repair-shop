package br.com.soat.customer

import br.com.soat.customer.model.CreateCustomerRequest
import br.com.soat.customer.model.Customer

class CustomerUseCase(
    private val repository: CustomerRepository
) {

    fun create(request: CreateCustomerRequest): Customer {
        return repository.create(
            Customer(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact
            )
        )
    }
}