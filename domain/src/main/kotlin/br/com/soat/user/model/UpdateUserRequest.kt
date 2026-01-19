package br.com.soat.user.model

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber

data class UpdateUserRequest(
    val name: String,
    val document: Document,
    val email: Email,
    val contact: PhoneNumber,
    val role: User.Role,
)
