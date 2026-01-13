package br.com.soat.shared.vo

class Email private constructor(val value: String) {

    init {
        require(value.isNotBlank()) { "Email cannot be blank" }
        require(value.length <= MAX_LENGTH) { "Email exceeds maximum length of $MAX_LENGTH characters" }
        require(value.matches(EMAIL_REGEX)) { "Invalid email format: $value" }
    }

    companion object {
        private const val MAX_LENGTH = 255

        private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

        operator fun invoke(value: String): Email {
            val normalized = value.trim().lowercase()
            return Email(normalized)
        }
    }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Email) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
