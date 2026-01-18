package br.com.soat.shared.dto

import br.com.soat.shared.model.Error

data class ErrorResponseDTO(
    val code: String,
    val message: String
)

fun Error.toErrorResponseDTO(customMessage: String? = null): ErrorResponseDTO {
    return ErrorResponseDTO(code = this.code, message = customMessage ?: this.message)
}