package br.com.soat.user.dto

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.user.model.UpdateUserRequest
import br.com.soat.user.model.User

data class UpdateUserRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val role: User.Role,
) {

    fun toModel() = UpdateUserRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact),
        role = role
    )
}
