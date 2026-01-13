package br.com.soat.shared.vo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmailTest {

    @Test
    fun `should create valid email`() {
        val email = Email("test@example.com")
        assertEquals("test@example.com", email.value)
    }

    @Test
    fun `should normalize email to lowercase`() {
        val email = Email("Test@EXAMPLE.COM")
        assertEquals("test@example.com", email.value)
    }

    @Test
    fun `should trim whitespace`() {
        val email = Email("  test@example.com  ")
        assertEquals("test@example.com", email.value)
    }

    @Test
    fun `should reject blank email`() {
        assertThrows<IllegalArgumentException> {
            Email("")
        }
    }

    @Test
    fun `should reject invalid email format`() {
        assertThrows<IllegalArgumentException> {
            Email("not-an-email")
        }
    }

    @Test
    fun `should reject email exceeding max length`() {
        val tooLong = "a".repeat(250) + "@example.com"
        assertThrows<IllegalArgumentException> {
            Email(tooLong)
        }
    }

    @Test
    fun `equals should work by value`() {
        val email1 = Email("test@example.com")
        val email2 = Email("TEST@example.com")
        assertEquals(email1, email2)
    }
}
