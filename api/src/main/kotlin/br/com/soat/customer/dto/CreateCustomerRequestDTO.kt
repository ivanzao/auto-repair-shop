package br.com.soat.customer.dto

import br.com.soat.customer.model.CreateCustomerRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class CreateCustomerRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {
    fun toModel() = CreateCustomerRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact)
    )
}