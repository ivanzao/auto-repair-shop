package br.com.soat.auth.model

data class AuthenticateRequest(
    val email: String,
    val password: String
)
