package br.com.soat.shared.vo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DocumentTest {

    @Nested
    inner class CpfTests {

        @Test
        fun `should create valid CPF`() {
            val document = Document("12345678909")
            assertEquals("12345678909", document.value)
            assertEquals(Document.Type.CPF, document.type)
            assertTrue(document.isCpf())
            assertFalse(document.isCnpj())
        }

        @Test
        fun `should normalize CPF by removing formatting`() {
            val document = Document("123.456.789-09")
            assertEquals("12345678909", document.value)
            assertEquals(Document.Type.CPF, document.type)
        }

        @Test
        fun `should format CPF correctly`() {
            val document = Document("12345678909")
            assertEquals("123.456.789-09", document.formatted())
        }
    }

    @Nested
    inner class CnpjTests {

        @Test
        fun `should create valid CNPJ`() {
            val document = Document("12345678000195")
            assertEquals("12345678000195", document.value)
            assertEquals(Document.Type.CNPJ, document.type)
            assertTrue(document.isCnpj())
            assertFalse(document.isCpf())
        }

        @Test
        fun `should normalize CNPJ by removing formatting`() {
            val document = Document("12.345.678/0001-95")
            assertEquals("12345678000195", document.value)
            assertEquals(Document.Type.CNPJ, document.type)
        }

        @Test
        fun `should format CNPJ correctly`() {
            val document = Document("12345678000195")
            assertEquals("12.345.678/0001-95", document.formatted())
        }
    }

    @Nested
    inner class ValidationTests {

        @Test
        fun `should reject blank document`() {
            assertThrows<IllegalArgumentException> {
                Document("")
            }
        }

        @Test
        fun `should reject document with invalid length`() {
            val exception = assertThrows<IllegalArgumentException> {
                Document("123456789") // 9 digits - neither CPF nor CNPJ
            }
            assertTrue(exception.message!!.contains("11 digits (CPF) or 14 digits (CNPJ)"))
        }

        @Test
        fun `should reject document with 12 digits`() {
            assertThrows<IllegalArgumentException> {
                Document("123456789012") // 12 digits
            }
        }

        @Test
        fun `should reject document with 13 digits`() {
            assertThrows<IllegalArgumentException> {
                Document("1234567890123") // 13 digits
            }
        }
    }

    @Nested
    inner class EqualityTests {

        @Test
        fun `CPF equals should work by value`() {
            val doc1 = Document("12345678909")
            val doc2 = Document("123.456.789-09")
            assertEquals(doc1, doc2)
        }

        @Test
        fun `CNPJ equals should work by value`() {
            val doc1 = Document("12345678000195")
            val doc2 = Document("12.345.678/0001-95")
            assertEquals(doc1, doc2)
        }

        @Test
        fun `CPF and CNPJ with same prefix should not be equal`() {
            val cpf = Document("12345678909")
            val cnpj = Document("12345678909012") // Different length, different document
            assertFalse(cpf == cnpj)
        }
    }
}
