package br.com.soat.shared.vo

class Document private constructor(val value: String, val type: Type) {

    init {
        require(value.isNotBlank()) { "Document cannot be blank" }
        require(value.all { it.isDigit() }) { "Document must contain only digits" }
        when (type) {
            Type.CPF -> require(value.length == CPF_LENGTH) { "CPF must have $CPF_LENGTH digits, got: ${value.length}" }
            Type.CNPJ -> require(value.length == CNPJ_LENGTH) { "CNPJ must have $CNPJ_LENGTH digits, got: ${value.length}" }
        }
    }

    enum class Type { CPF, CNPJ }

    companion object {
        private const val CPF_LENGTH = 11
        private const val CNPJ_LENGTH = 14

        operator fun invoke(value: String): Document {
            val normalized = value.filter { it.isDigit() }
            val type = when (normalized.length) {
                CPF_LENGTH -> Type.CPF
                CNPJ_LENGTH -> Type.CNPJ
                else -> throw IllegalArgumentException(
                    "Document must have $CPF_LENGTH digits (CPF) or $CNPJ_LENGTH digits (CNPJ), got: ${normalized.length}"
                )
            }
            return Document(normalized, type)
        }
    }

    fun formatted(): String = when (type) {
        Type.CPF -> "${value.substring(0, 3)}.${value.substring(3, 6)}.${value.substring(6, 9)}-${value.substring(9)}"
        Type.CNPJ -> "${value.substring(0, 2)}.${value.substring(2, 5)}.${value.substring(5, 8)}/${value.substring(8, 12)}-${value.substring(12)}"
    }

    fun isCpf(): Boolean = type == Type.CPF
    fun isCnpj(): Boolean = type == Type.CNPJ

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Document) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
