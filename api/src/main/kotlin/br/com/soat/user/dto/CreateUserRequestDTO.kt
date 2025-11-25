package br.com.soat.user.dto

import br.com.soat.user.model.CreateUserRequest
import br.com.soat.user.model.User

data class CreateUserRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val role: User.Role,
) {
    fun toModel() = CreateUserRequest(
        name = name,
        document = document,
        email = email,
        contact = contact,
        role = role
    )
}