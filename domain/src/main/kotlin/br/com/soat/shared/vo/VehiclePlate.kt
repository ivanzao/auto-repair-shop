package br.com.soat.shared.vo

class VehiclePlate private constructor(val value: String) {

    init {
        require(value.isNotBlank()) { "Vehicle plate cannot be blank" }
        require(value.length == PLATE_LENGTH) { "Vehicle plate must have $PLATE_LENGTH characters, got: ${value.length}" }
        require(PLATE_REGEX.matches(value)) { "Invalid vehicle plate format: $value" }
    }

    companion object {
        private const val PLATE_LENGTH = 7
        private val PLATE_REGEX = "^[A-Z]{3}\\d{4}$".toRegex()

        operator fun invoke(value: String): VehiclePlate {
            val normalized = value.replace("-", "").replace(" ", "").uppercase()
            return VehiclePlate(normalized)
        }
    }

    fun formatted(): String = "${value.take(3)}-${value.substring(3)}"

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VehiclePlate) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
