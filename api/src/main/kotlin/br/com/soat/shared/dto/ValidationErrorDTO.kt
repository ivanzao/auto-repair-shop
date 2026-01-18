package br.com.soat.shared.dto

import br.com.soat.shared.model.Error

data class ValidationErrorDTO(
    val fieldErrors: List<FieldError>
) {
    val code = Error.BAD_REQUEST.code
    val message = Error.BAD_REQUEST.message
}

data class FieldError(
    val field: String,
    val message: String
)
