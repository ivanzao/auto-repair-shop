package br.com.soat.user.dto

import br.com.soat.br.com.soat.user.model.User

data class UserResponseDTO(
    val id: String,
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val role: String,
) {
    companion object {
        fun from(user: User) = UserResponseDTO(
            id = user.id.toString(),
            name = user.name,
            document = user.document,
            email = user.email,
            contact = user.contact,
            role = user.role.name,
        )
    }
}
