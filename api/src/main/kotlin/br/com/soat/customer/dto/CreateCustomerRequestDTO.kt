package br.com.soat.customer.dto

import br.com.soat.customer.model.CreateCustomerRequest

data class CreateCustomerRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {
    fun toModel() = CreateCustomerRequest(
        name = name,
        document = document,
        email = email,
        contact = contact
    )
}