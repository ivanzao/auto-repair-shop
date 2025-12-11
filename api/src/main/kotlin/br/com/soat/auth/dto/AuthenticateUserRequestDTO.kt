package br.com.soat.auth.dto

import br.com.soat.auth.model.AuthenticateRequest

data class AuthenticateUserRequestDTO(
    val email: String,
    val password: String
) {
    fun toModel() = AuthenticateRequest(email, password)
}