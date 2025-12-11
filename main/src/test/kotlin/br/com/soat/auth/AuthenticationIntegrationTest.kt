package br.com.soat.auth

import br.com.soat.IntegrationTest
import br.com.soat.auth.dto.AuthenticateUserRequestDTO
import br.com.soat.auth.dto.AuthenticateUserResponseDTO
import br.com.soat.auth.dto.RefreshTokenRequestDTO
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import br.com.soat.user.model.User
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthenticationIntegrationTest : IntegrationTest() {

    private val admin = User(
        name = "Admin",
        hashedPassword = "",
        document = "99999999999",
        email = "dummy@mail.com",
        contact = "(11) 99999-9999",
        role = User.Role.ADMIN
    )

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    private val adminPassword = "password123"
    private lateinit var adminUser: UserResponseDTO

    @BeforeEach
    fun setupAdmin() {
        adminUser = createUser("admin@example.com", adminPassword)
    }

    @Test
    fun `should login and return 201 with tokens`() {
        val requestDto = AuthenticateUserRequestDTO(
            email = adminUser.email,
            password = adminPassword
        )

        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/login"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")

        val responseDto: AuthenticateUserResponseDTO = mapper.readValue(response.body())
        assertNotNull(responseDto.accessToken)
        assertNotNull(responseDto.refreshToken)
        assertNotNull(responseDto.expiresAt)
    }

    @Test
    fun `should refresh token and return 201 with new tokens`() {
        // First login to get refresh token
        val loginRequestDto = AuthenticateUserRequestDTO(
            email = adminUser.email,
            password = adminPassword
        )
        val loginBody = mapper.writeValueAsString(loginRequestDto)
        val loginRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(loginBody))
            .build()
        val loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString())
        val loginResponseDto: AuthenticateUserResponseDTO = mapper.readValue(loginResponse.body())

        // Use refresh token to get new tokens
        val refreshRequestDto = RefreshTokenRequestDTO(
            refreshToken = loginResponseDto.refreshToken
        )
        val refreshBody = mapper.writeValueAsString(refreshRequestDto)
        val refreshRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/refresh"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(refreshBody))
            .build()

        val refreshResponse = client.send(refreshRequest, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, refreshResponse.statusCode(), "HTTP status code must be 201 Created")

        val refreshResponseDto: AuthenticateUserResponseDTO = mapper.readValue(refreshResponse.body())
        assertNotNull(refreshResponseDto.accessToken)
        assertNotNull(refreshResponseDto.refreshToken)
        assertNotNull(refreshResponseDto.expiresAt)
        // Verify we got a new refresh token (different from the original)
        assertEquals(false, loginResponseDto.refreshToken == refreshResponseDto.refreshToken)
    }

    private fun createUser(email: String, password: String): UserResponseDTO {
        val tokenProvider = koinApplication!!.koin.get<AuthenticationTokenProvider>()
        val requestDto = CreateUserRequestDTO(
            name = "Test User",
            document = "12345678900",
            email = email,
            contact = "+55 11 99999-9999",
            password = password,
            role = User.Role.ADMIN
        )

        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/users"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${tokenProvider.generate(admin, LocalDateTime.now().plusDays(1))}")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 201) {
            throw IllegalStateException("Failed to create user. Status: ${response.statusCode()}, Body: ${response.body()}")
        }

        return mapper.readValue(response.body())
    }
}