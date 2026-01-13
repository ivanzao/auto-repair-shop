package br.com.soat.shared.vo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DocumentTest {

    @Test
    fun `should create valid CPF`() {
        val document = Document("12345678909")
        assertEquals("12345678909", document.value)
    }

    @Test
    fun `should normalize CPF by removing formatting`() {
        val document = Document("123.456.789-09")
        assertEquals("12345678909", document.value)
    }

    @Test
    fun `should format CPF correctly`() {
        val document = Document("12345678909")
        assertEquals("123.456.789-09", document.formatted())
    }

    @Test
    fun `should reject CPF with invalid checksum`() {
        assertThrows<IllegalArgumentException> {
            Document("12345678900")
        }
    }

    @Test
    fun `should reject CPF with all same digits`() {
        assertThrows<IllegalArgumentException> {
            Document("11111111111")
        }
    }

    @Test
    fun `should reject blank CPF`() {
        assertThrows<IllegalArgumentException> {
            Document("")
        }
    }

    @Test
    fun `should reject CPF with invalid length`() {
        assertThrows<IllegalArgumentException> {
            Document("123456789")
        }
    }

    @Test
    fun `equals should work by value`() {
        val doc1 = Document("12345678909")
        val doc2 = Document("123.456.789-09")
        assertEquals(doc1, doc2)
    }
}
