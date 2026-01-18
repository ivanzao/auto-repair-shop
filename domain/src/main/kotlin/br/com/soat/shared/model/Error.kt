package br.com.soat.shared.model

enum class Error(val code: String, val message: String) {
    INTERNAL_SERVER_ERROR("ARS-001", "Internal server error"),

    BAD_REQUEST("ARS-400", "Invalid request")
}
