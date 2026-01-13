package br.com.soat.auth.dto

import br.com.soat.auth.model.AuthenticateRequest
import br.com.soat.shared.vo.Email

data class AuthenticateUserRequestDTO(
    val email: String,
    val password: String
) {
    fun toModel() = AuthenticateRequest(Email(email), password)
}