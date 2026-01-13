package br.com.soat.shared.vo

class Document private constructor(val value: String) {

    init {
        require(value.isNotBlank()) { "CPF cannot be blank" }
        require(value.length == CPF_LENGTH) { "CPF must have $CPF_LENGTH digits, got: ${value.length}" }
        require(value.all { it.isDigit() }) { "CPF must contain only digits" }
        require(!isInvalidSequence(value)) { "CPF cannot be a sequence of repeated digits" }
        require(isValidChecksum(value)) { "Invalid CPF checksum" }
    }

    companion object {
        private const val CPF_LENGTH = 11

        operator fun invoke(value: String): Document {
            val normalized = value.filter { it.isDigit() }
            return Document(normalized)
        }

        private fun isInvalidSequence(cpf: String): Boolean {
            return cpf.all { it == cpf.first() }
        }

        private fun isValidChecksum(cpf: String): Boolean {
            val digit1 = calculateDigit(cpf.take(9), 10)
            val digit2 = calculateDigit(cpf.take(9) + digit1, 11)
            return cpf == cpf.take(9) + digit1 + digit2
        }

        private fun calculateDigit(partial: String, weight: Int): Char {
            val sum = partial.mapIndexed { index, char ->
                char.digitToInt() * (weight - index)
            }.sum()
            val remainder = sum % 11
            return if (remainder < 2) '0' else ('0' + (11 - remainder))
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
