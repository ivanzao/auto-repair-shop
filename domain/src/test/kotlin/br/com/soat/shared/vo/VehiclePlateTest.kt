package br.com.soat.shared.vo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VehiclePlateTest {

    @Test
    fun `should create valid plate`() {
        val plate = VehiclePlate("ABC1234")
        assertEquals("ABC1234", plate.value)
    }

    @Test
    fun `should normalize plate with hyphen`() {
        val plate = VehiclePlate("ABC-1234")
        assertEquals("ABC1234", plate.value)
    }

    @Test
    fun `should normalize plate to uppercase`() {
        val plate = VehiclePlate("abc1234")
        assertEquals("ABC1234", plate.value)
    }

    @Test
    fun `should normalize plate with hyphen and lowercase`() {
        val plate = VehiclePlate("abc-1234")
        assertEquals("ABC1234", plate.value)
    }

    @Test
    fun `should format plate correctly`() {
        val plate = VehiclePlate("ABC1234")
        assertEquals("ABC-1234", plate.formatted())
    }

    @Test
    fun `should reject blank plate`() {
        assertThrows<IllegalArgumentException> {
            VehiclePlate("")
        }
    }

    @Test
    fun `should reject plate with invalid length`() {
        assertThrows<IllegalArgumentException> {
            VehiclePlate("ABC123")
        }
    }

    @Test
    fun `should reject plate with all letters`() {
        assertThrows<IllegalArgumentException> {
            VehiclePlate("ABCDEFG")
        }
    }

    @Test
    fun `should reject plate with all digits`() {
        assertThrows<IllegalArgumentException> {
            VehiclePlate("1234567")
        }
    }

    @Test
    fun `equals should work by value`() {
        val plate1 = VehiclePlate("ABC1234")
        val plate2 = VehiclePlate("abc-1234")
        assertEquals(plate1, plate2)
    }
}
