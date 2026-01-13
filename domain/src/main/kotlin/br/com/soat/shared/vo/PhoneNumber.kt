package br.com.soat.shared.vo

class PhoneNumber private constructor(val value: String) {

    init {
        require(value.isNotBlank()) { "Phone number cannot be blank" }
        require(value.length == PHONE_LENGTH) { "Phone number must have $PHONE_LENGTH digits, got: ${value.length}" }
        require(value.all { it.isDigit() }) { "Phone number must contain only digits" }

        val ddd = value.take(2).toInt()
        require(ddd in MIN_DDD..MAX_DDD) { "Invalid area code (DDD): $ddd" }

        val firstDigit = value[2]
        require(firstDigit == '9') { "Mobile number must start with 9 after DDD" }
    }

    companion object {
        private const val PHONE_LENGTH = 11
        private const val MIN_DDD = 11
        private const val MAX_DDD = 99

        operator fun invoke(value: String): PhoneNumber {
            val normalized = value.filter { it.isDigit() }
            return PhoneNumber(normalized)
        }
    }

    fun formatted(): String = "(${value.take(2)}) ${value.substring(2, 7)}-${value.substring(7)}"

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhoneNumber) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
