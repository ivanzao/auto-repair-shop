package br.com.soat.customer.model

data class CreateCustomerRequest(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
)
