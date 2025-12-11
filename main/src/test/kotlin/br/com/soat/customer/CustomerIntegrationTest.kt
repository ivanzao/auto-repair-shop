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

    @Test
    fun `should get customer by id`() {
        // Create
        val createRequestDto = CreateCustomerRequestDTO(
            name = "Fulano",
            document = "12345678900",
            email = "fulano@example.com",
            contact = "+55 11 99999-9999"
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdCustomer: CustomerResponseDTO = mapper.readValue(createResponse.body())

        // Get
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers/${createdCustomer.id}"))
            .GET()
            .build()
        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, getResponse.statusCode())
        val fetchedCustomer: CustomerResponseDTO = mapper.readValue(getResponse.body())
        assertEquals(createdCustomer.id, fetchedCustomer.id)
        assertEquals(createRequestDto.name, fetchedCustomer.name)
        assertEquals(createRequestDto.document, fetchedCustomer.document)
        assertEquals(createRequestDto.email, fetchedCustomer.email)
        assertEquals(createRequestDto.contact, fetchedCustomer.contact)
    }

    @Test
    fun `should update customer`() {
        // Create
        val createRequestDto = CreateCustomerRequestDTO(
            name = "Ciclano",
            document = "11122233344",
            email = "ciclano@example.com",
            contact = "+55 11 77777-7777"
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdCustomer: CustomerResponseDTO = mapper.readValue(createResponse.body())

        // Update
        val updateRequestDto = CreateCustomerRequestDTO(
            name = "Ciclano Updated",
            document = "11122233344",
            email = "ciclano_updated@example.com",
            contact = "+55 11 77777-7777"
        )
        val updateBody = mapper.writeValueAsString(updateRequestDto)
        val updateRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers/${createdCustomer.id}"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(updateBody))
            .build()
        val updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, updateResponse.statusCode())
        val updatedCustomer: CustomerResponseDTO = mapper.readValue(updateResponse.body())
        assertEquals(updateRequestDto.name, updatedCustomer.name)
        assertEquals(updateRequestDto.document, updatedCustomer.document)
        assertEquals(updateRequestDto.email, updatedCustomer.email)
        assertEquals(updateRequestDto.contact, updatedCustomer.contact)
    }

    @Test
    fun `should delete customer`() {
        // Create
        val createRequestDto = CreateCustomerRequestDTO(
            name = "To Delete",
            document = "00000000000",
            email = "delete@example.com",
            contact = "+55 11 00000-0000"
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdCustomer: CustomerResponseDTO = mapper.readValue(createResponse.body())

        // Delete
        val deleteRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers/${createdCustomer.id}"))
            .DELETE()
            .build()
        val deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(204, deleteResponse.statusCode())

        // Verify Not Found
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers/${createdCustomer.id}"))
            .GET()
            .build()
        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, getResponse.statusCode())
    }
}
