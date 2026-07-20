package br.com.soat.customer

import br.com.soat.IntegrationTest
import br.com.soat.customer.repository.CustomerRepository
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomerIntegrationTest : IntegrationTest() {

    private val customerRepository: CustomerRepository by lazy { get<CustomerRepository>() }

    @Test
    fun `should get customer by id`() {
        val authHeaders = attendantHeaders()

        val createRequestDto = CreateCustomerRequestDTO(
            name = "Fulano",
            document = "12345678900",
            email = "fulano@example.com",
            contact = "11999999999"
        )

        val createResponse = http.createCustomer(createRequestDto, authHeaders)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val customerId = createResponse.body().id
        val getResponse = http.getCustomer(customerId, authHeaders)
        assertEquals(200, getResponse.statusCode(), "HTTP status code must be 200 OK")

        val fetchedCustomer = customerRepository.findById(UUID.fromString(customerId))!!
        assertEquals(createRequestDto.name, fetchedCustomer.name)
        assertEquals(createRequestDto.document, fetchedCustomer.document.value)
        assertEquals(createRequestDto.email, fetchedCustomer.email.value)
        assertEquals(createRequestDto.contact, fetchedCustomer.contact.value)
    }

    @Test
    fun `should create customer successfully`() {
        val authHeaders = adminHeaders()

        val requestDto = CreateCustomerRequestDTO(
            name = "Beltrano",
            document = "98765432100",
            email = "beltrano@example.com",
            contact = "11988888888"
        )

        val createCustomerResponse = http.createCustomer(requestDto, authHeaders)
        assertEquals(201, createCustomerResponse.statusCode(), "HTTP status code must be 201 Created")

        val createdCustomer = customerRepository.findById(UUID.fromString(createCustomerResponse.body().id))!!
        assertEquals(requestDto.name, createdCustomer.name)
        assertEquals(requestDto.document, createdCustomer.document.value)
        assertEquals(requestDto.email, createdCustomer.email.value)
        assertEquals(requestDto.contact, createdCustomer.contact.value)
    }

    @Test
    fun `should update customer`() {
        val authHeaders = adminHeaders()

        val createRequestDto = CreateCustomerRequestDTO(
            name = "Ciclano",
            document = "11122233344",
            email = "ciclano@example.com",
            contact = "11 97777-7777"
        )

        val createResponse = http.createCustomer(createRequestDto, authHeaders)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val customerId = createResponse.body().id
        val updateRequestDto = CreateCustomerRequestDTO(
            name = "Ciclano Updated",
            document = "11122233344",
            email = "ciclano_updated@example.com",
            contact = "11977777777"
        )

        val updateResponse = http.updateCustomer(customerId, updateRequestDto, authHeaders)
        assertEquals(200, updateResponse.statusCode(), "HTTP status code must be 200 OK")

        val updatedCustomer = customerRepository.findById(UUID.fromString(customerId))!!
        assertEquals(updateRequestDto.name, updatedCustomer.name)
        assertEquals(updateRequestDto.document, updatedCustomer.document.value)
        assertEquals(updateRequestDto.email, updatedCustomer.email.value)
        assertEquals(updateRequestDto.contact, updatedCustomer.contact.value)
    }

    @Test
    fun `should delete customer`() {
        val authHeaders = adminHeaders()

        val createRequestDto = CreateCustomerRequestDTO(
            name = "To Delete",
            document = "00000000000",
            email = "delete@example.com",
            contact = "11 90000-0000"
        )

        val createResponse = http.createCustomer(createRequestDto, authHeaders)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val customerId = createResponse.body().id
        val deleteResponse = http.deleteCustomer(customerId, authHeaders)
        assertEquals(204, deleteResponse.statusCode(), "HTTP status code must be 204 No Content")

        val getResponse = http.getCustomer(customerId, authHeaders)
        assertEquals(404, getResponse.statusCode(), "HTTP status code must be 404 Not Found")

        val deletedCustomer = customerRepository.findById(UUID.fromString(customerId))
        assertNull(deletedCustomer, "Customer should be deleted from database")
    }
}
