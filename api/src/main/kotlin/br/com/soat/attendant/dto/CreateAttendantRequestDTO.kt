package br.com.soat.attendant.dto

import br.com.soat.attendant.model.CreateAttendantRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class CreateAttendantRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {
    fun toModel() = CreateAttendantRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact),
    )
}
