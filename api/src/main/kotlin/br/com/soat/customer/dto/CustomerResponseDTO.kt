package br.com.soat.customer.dto

import br.com.soat.customer.model.Customer

data class CustomerResponseDTO(
    val id: String,
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {

    companion object {
        fun from(customer: Customer) = CustomerResponseDTO(
            id = customer.id.toString(),
            name = customer.name,
            document = customer.document,
            email = customer.email,
            contact = customer.contact
        )
    }
}