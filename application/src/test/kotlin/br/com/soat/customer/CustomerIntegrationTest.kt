package br.com.soat.customer

import br.com.soat.IntegrationTest
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomerIntegrationTest : IntegrationTest() {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Test
    fun `should create customer and return 201 with payload`() {
        val requestDto = CreateCustomerRequestDTO(
            name = "Beltrano",
            document = "98765432100",
            email = "beltrano@example.com",
            contact = "+55 11 88888-8888"
        )

        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")

        val responseDto: CustomerResponseDTO = mapper.readValue(response.body())
        assertEquals(requestDto.name, responseDto.name)
        assertEquals(requestDto.document, responseDto.document)
        assertEquals(requestDto.email, responseDto.email)
        assertEquals(requestDto.contact, responseDto.contact)
    }
}
