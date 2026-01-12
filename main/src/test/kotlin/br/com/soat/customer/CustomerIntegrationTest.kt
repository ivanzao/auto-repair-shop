package br.com.soat.customer

import br.com.soat.IntegrationTest
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.user.createUser
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomerIntegrationTest : IntegrationTest() {

    private val customerRepository: CustomerRepository by lazy { get<CustomerRepository>() }
    private val tokenProvider: AuthenticationTokenProvider by lazy { get<AuthenticationTokenProvider>() }

    @Test
    fun `should create customer successfully`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val requestDto = CreateCustomerRequestDTO(
            name = "Beltrano",
            document = "98765432100",
            email = "beltrano@example.com",
            contact = "+55 11 88888-8888"
        )

        val createCustomerResponse = http.createCustomer(requestDto, bearerToken)
        assertEquals(201, createCustomerResponse.statusCode(), "HTTP status code must be 201 Created")

        val createdCustomer = customerRepository.findById(UUID.fromString(createCustomerResponse.body().id))!!
        assertEquals(requestDto.name, createdCustomer.name)
        assertEquals(requestDto.document, createdCustomer.document)
        assertEquals(requestDto.email, createdCustomer.email)
        assertEquals(requestDto.contact, createdCustomer.contact)
    }

    @Test
    fun `should get customer by id`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateCustomerRequestDTO(
            name = "Fulano",
            document = "12345678900",
            email = "fulano@example.com",
            contact = "+55 11 99999-9999"
        )

        val createResponse = http.createCustomer(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val customerId = createResponse.body().id
        val getResponse = http.getCustomer(customerId, bearerToken)
        assertEquals(200, getResponse.statusCode(), "HTTP status code must be 200 OK")

        val fetchedCustomer = customerRepository.findById(UUID.fromString(customerId))!!
        assertEquals(createRequestDto.name, fetchedCustomer.name)
        assertEquals(createRequestDto.document, fetchedCustomer.document)
        assertEquals(createRequestDto.email, fetchedCustomer.email)
        assertEquals(createRequestDto.contact, fetchedCustomer.contact)
    }

    @Test
    fun `should update customer`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateCustomerRequestDTO(
            name = "Ciclano",
            document = "11122233344",
            email = "ciclano@example.com",
            contact = "+55 11 77777-7777"
        )

        val createResponse = http.createCustomer(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val customerId = createResponse.body().id
        val updateRequestDto = CreateCustomerRequestDTO(
            name = "Ciclano Updated",
            document = "11122233344",
            email = "ciclano_updated@example.com",
            contact = "+55 11 77777-7777"
        )

        val updateResponse = http.updateCustomer(customerId, updateRequestDto, bearerToken)
        assertEquals(200, updateResponse.statusCode(), "HTTP status code must be 200 OK")

        val updatedCustomer = customerRepository.findById(UUID.fromString(customerId))!!
        assertEquals(updateRequestDto.name, updatedCustomer.name)
        assertEquals(updateRequestDto.document, updatedCustomer.document)
        assertEquals(updateRequestDto.email, updatedCustomer.email)
        assertEquals(updateRequestDto.contact, updatedCustomer.contact)
    }

    @Test
    fun `should delete customer`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateCustomerRequestDTO(
            name = "To Delete",
            document = "00000000000",
            email = "delete@example.com",
            contact = "+55 11 00000-0000"
        )

        val createResponse = http.createCustomer(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val customerId = createResponse.body().id
        val deleteResponse = http.deleteCustomer(customerId, bearerToken)
        assertEquals(204, deleteResponse.statusCode(), "HTTP status code must be 204 No Content")

        val getResponse = http.getCustomer(customerId, bearerToken)
        assertEquals(404, getResponse.statusCode(), "HTTP status code must be 404 Not Found")

        val deletedCustomer = customerRepository.findById(UUID.fromString(customerId))
        assertNull(deletedCustomer, "Customer should be deleted from database")
    }
}
