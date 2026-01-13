package br.com.soat.auth.model

import br.com.soat.shared.vo.Email

data class AuthenticateRequest(
    val email: Email,
    val password: String
)
