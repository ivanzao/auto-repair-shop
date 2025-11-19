package br.com.soat.br.com.soat.user.model

data class CreateUserRequest(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val role: User.Role,
)
