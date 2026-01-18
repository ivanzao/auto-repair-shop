package br.com.soat.user

import br.com.soat.IntegrationTest
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.model.User
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserIntegrationTest : IntegrationTest() {

    private val userRepository: UserRepository by lazy { get<UserRepository>() }
    private val tokenProvider: AuthenticationTokenProvider by lazy { get<AuthenticationTokenProvider>() }

    @Test
    fun `should create user successfully`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val requestDto = CreateUserRequestDTO(
            name = "Fulano",
            document = "12345678900",
            email = "fulano@example.com",
            contact = "11999999999",
            password = "password123",
            role = User.Role.ADMIN,
        )

        val createUserResponse = http.createUser(requestDto, bearerToken)
        assertEquals(201, createUserResponse.statusCode(), "HTTP status code must be 201 Created")

        val createdUser = userRepository.findById(UUID.fromString(createUserResponse.body().id))!!
        assertEquals(requestDto.name, createdUser.name)
        assertEquals(requestDto.document, createdUser.document.value)
        assertEquals(requestDto.email, createdUser.email.value)
        assertEquals(requestDto.contact, createdUser.contact.value)
        assertEquals(requestDto.role, createdUser.role)
    }
}