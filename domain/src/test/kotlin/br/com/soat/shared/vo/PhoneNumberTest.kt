package br.com.soat.shared.vo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PhoneNumberTest {

    @Test
    fun `should create valid mobile phone`() {
        val phone = PhoneNumber("11987654321")
        assertEquals("11987654321", phone.value)
    }

    @Test
    fun `should normalize phone by removing formatting`() {
        val phone = PhoneNumber("(11) 98765-4321")
        assertEquals("11987654321", phone.value)
    }

    @Test
    fun `should format phone correctly`() {
        val phone = PhoneNumber("11987654321")
        assertEquals("(11) 98765-4321", phone.formatted())
    }

    @Test
    fun `should reject phone without 9 after DDD`() {
        assertThrows<IllegalArgumentException> {
            PhoneNumber("11887654321")
        }
    }

    @Test
    fun `should reject phone with invalid length`() {
        assertThrows<IllegalArgumentException> {
            PhoneNumber("119876543")
        }
    }

    @Test
    fun `should reject blank phone`() {
        assertThrows<IllegalArgumentException> {
            PhoneNumber("")
        }
    }

    @Test
    fun `should reject phone with invalid DDD`() {
        assertThrows<IllegalArgumentException> {
            PhoneNumber("10987654321")
        }
    }

    @Test
    fun `equals should work by value`() {
        val phone1 = PhoneNumber("11987654321")
        val phone2 = PhoneNumber("(11) 98765-4321")
        assertEquals(phone1, phone2)
    }
}
