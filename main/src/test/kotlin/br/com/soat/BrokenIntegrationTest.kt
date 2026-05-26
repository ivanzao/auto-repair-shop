package br.com.soat

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BrokenIntegrationTest : IntegrationTest() {

    @Test
    fun `should fail`() {
        Assertions.fail<String>()
    }
}