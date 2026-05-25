package br.com.soat.attendant.dto

import br.com.soat.attendant.model.UpdateAttendantRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class UpdateAttendantRequestDTO(
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
) {
    fun toModel() = UpdateAttendantRequest(
        name = name,
        document = Document(document),
        email = Email(email),
        contact = PhoneNumber(contact),
    )
}
