package br.com.soat.auth

import br.com.soat.IntegrationTest
import br.com.soat.auth.dto.AuthenticateUserRequestDTO
import br.com.soat.auth.dto.RefreshTokenRequestDTO
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import br.com.soat.user.model.User
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthenticationIntegrationTest : IntegrationTest() {

    private val admin = User(
        name = "Admin",
        hashedPassword = "",
        document = Document("99999999999"),
        email = Email("dummy@mail.com"),
        contact = PhoneNumber("11999999999"),
        role = User.Role.ADMIN
    )

    private val adminPassword = "password123"
    private lateinit var adminUser: UserResponseDTO

    @BeforeEach
    fun setupAdmin() {
        adminUser = createUser("admin@example.com", adminPassword)
    }

    @Test
    fun `should login and return 201 with tokens`() {
        val response = http.login(
            AuthenticateUserRequestDTO(
                email = adminUser.email,
                password = adminPassword
            )
        )

        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")

        val responseDto = response.body()
        assertNotNull(responseDto.accessToken)
        assertNotNull(responseDto.refreshToken)
        assertNotNull(responseDto.expiresAt)
    }

    @Test
    fun `should refresh token and return 201 with new tokens`() {
        val response = http.login(
            AuthenticateUserRequestDTO(
                email = adminUser.email,
                password = adminPassword
            )
        )

        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")
        val loginResponseDto = response.body()

        val refreshResponse = http.refreshToken(RefreshTokenRequestDTO(loginResponseDto.refreshToken))

        assertEquals(201, refreshResponse.statusCode(), "HTTP status code must be 201 Created")
        val refreshResponseDto = refreshResponse.body()

        assertNotNull(refreshResponseDto.accessToken)
        assertNotNull(refreshResponseDto.refreshToken)
        assertNotNull(refreshResponseDto.expiresAt)

        assertEquals(false, loginResponseDto.refreshToken == refreshResponseDto.refreshToken)
    }

    private fun createUser(email: String, password: String): UserResponseDTO {
        val tokenProvider = koinApplication.koin.get<AuthenticationTokenProvider>()
        val requestDto = CreateUserRequestDTO(
            name = "Test User",
            document = "12345678900",
            email = email,
            contact = "+55 11 99999-9999",
            password = password,
            role = User.Role.ADMIN
        )

        val response = http.createUser(requestDto, tokenProvider.generate(admin, LocalDateTime.now().plusDays(1)))

        if (response.statusCode() != 201) {
            throw IllegalStateException("Failed to create user. Status: ${response.statusCode()}, Body: ${response.body()}")
        }

        return response.body()
    }
}