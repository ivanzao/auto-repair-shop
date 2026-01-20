package br.com.soat.user

import br.com.soat.IntegrationTest
import br.com.soat.user.dto.UpdateUserRequestDTO
import br.com.soat.user.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserIntegrationTest : IntegrationTest() {

    @Test
    fun `should get user by id`() {
        val token = loginAsAdmin()
        val createdUser = httpClient.createUser(UserFixtures.createUserRequest(), token)

        val response = httpClient.getUser(createdUser.body().id.toString(), token)

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains(createdUser.body().id.toString()))
        assertTrue(response.body().contains("Test User"))
    }

    @Test
    fun `should return 404 when user not found`() {
        val token = loginAsAdmin()

        val response = httpClient.getUser("00000000-0000-0000-0000-000000000000", token)

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `should list all users`() {
        val token = loginAsAdmin()
        httpClient.createUser(UserFixtures.createUserRequest(), token)
        httpClient.createUser(UserFixtures.createUserRequest(
            document = "22233344455",
            email = "another@email.com"
        ), token)

        val response = httpClient.getAllUsers(token)

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Test User"))
    }

    @Test
    fun `should return 400 for invalid uuid format`() {
        val token = loginAsAdmin()

        val response = httpClient.getUser("invalid-uuid", token)

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `should create user successfully`() {
        val token = loginAsAdmin()

        val response = httpClient.createUser(UserFixtures.createUserRequest(), token)

        assertEquals(201, response.statusCode())
        assertEquals("Test User", response.body().name)
        assertEquals("test@email.com", response.body().email)
        assertEquals("ADMIN", response.body().role)
    }

    @Test
    fun `should update user`() {
        val token = loginAsAdmin()
        val createdUser = httpClient.createUser(UserFixtures.createUserRequest(), token)

        val updateRequest = UpdateUserRequestDTO(
            name = "Updated Name",
            document = "11122233344",
            email = "updated@email.com",
            contact = "11988887777",
            role = User.Role.ATTENDANT
        )

        val response = httpClient.updateUser(createdUser.body().id.toString(), updateRequest, token)

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Updated Name"))
        assertTrue(response.body().contains("updated@email.com"))
        assertTrue(response.body().contains("ATTENDANT"))
    }

    @Test
    fun `should return 404 when updating non-existent user`() {
        val token = loginAsAdmin()

        val updateRequest = UpdateUserRequestDTO(
            name = "Updated Name",
            document = "11122233344",
            email = "updated@email.com",
            contact = "11988887777",
            role = User.Role.ATTENDANT
        )

        val response = httpClient.updateUser("00000000-0000-0000-0000-000000000000", updateRequest, token)

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `should delete user`() {
        val token = loginAsAdmin()
        val createdUser = httpClient.createUser(UserFixtures.createUserRequest(), token)

        val deleteResponse = httpClient.deleteUser(createdUser.body().id, token)
        assertEquals(204, deleteResponse.statusCode())

        val getResponse = httpClient.getUser(createdUser.body().id, token)
        assertEquals(404, getResponse.statusCode())
    }
}
