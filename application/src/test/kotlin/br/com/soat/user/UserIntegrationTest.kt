package br.com.soat.user

import br.com.soat.IntegrationTest
import br.com.soat.assertIsUUID
import br.com.soat.user.model.User
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserIntegrationTest : IntegrationTest() {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Test
    fun `should create user and return 201 with payload`() {
        val requestDto = CreateUserRequestDTO(
            name = "Fulano",
            document = "12345678900",
            email = "fulano@example.com",
            contact = "+55 11 99999-9999",
            role = User.Role.ADMIN,
        )

        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/users"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")

        val responseDto: UserResponseDTO = mapper.readValue(response.body())
        assertIsUUID(responseDto.id, "Response must contain a valid UUID")
        assertEquals("Fulano", responseDto.name, "Response must contain name")
        assertEquals("12345678900", responseDto.document, "Response must contain document")
        assertEquals("fulano@example.com", responseDto.email, "Response must contain email")
        assertEquals("+55 11 99999-9999", responseDto.contact, "Response must contain contact")
        assertEquals("ADMIN", responseDto.role, "Response must contain role")
    }
}