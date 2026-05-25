package br.com.soat.attendant.model

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class UpdateAttendantRequest(
    val name: String,
    val document: Document,
    val email: Email,
    val contact: PhoneNumber,
)
