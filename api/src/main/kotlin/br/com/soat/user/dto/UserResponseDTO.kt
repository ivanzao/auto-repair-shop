package br.com.soat.user.dto

import br.com.soat.user.model.User

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
            document = user.document.value,
            email = user.email.value,
            contact = user.contact.value,
            role = user.role.name,
        )
    }
}
