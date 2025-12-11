package br.com.soat.user.model

data class CreateUserRequest(
    val name: String,
    val document: String,
    val password: String,
    val email: String,
    val contact: String,
    val role: User.Role,
)
