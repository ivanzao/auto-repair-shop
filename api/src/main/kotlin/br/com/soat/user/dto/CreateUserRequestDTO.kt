package br.com.soat.user.dto

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.user.model.CreateUserRequest
import br.com.soat.user.model.User

data class CreateUserRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val role: User.Role,
    val password: String,
) {

    fun toModel() = CreateUserRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact),
        role = role,
        password = password
    )
}