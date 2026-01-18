package br.com.soat.shared.vo

class Document private constructor(val value: String) {

    init {
        require(value.isNotBlank()) { "CPF cannot be blank" }
        require(value.length == CPF_LENGTH) { "CPF must have $CPF_LENGTH digits, got: ${value.length}" }
        require(value.all { it.isDigit() }) { "CPF must contain only digits" }
    }

    companion object {
        private const val CPF_LENGTH = 11

        operator fun invoke(value: String): Document {
            val normalized = value.filter { it.isDigit() }
            return Document(normalized)
        }
    }

    fun formatted(): String = "${value.take(3)}.${value.substring(3, 6)}.${value.substring(6, 9)}-${value.substring(9)}"

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Document) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
