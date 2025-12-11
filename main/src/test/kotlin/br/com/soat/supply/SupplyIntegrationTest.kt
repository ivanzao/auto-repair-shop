package br.com.soat.supply

import br.com.soat.IntegrationTest
import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.supply.dto.SupplyResponseDTO
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SupplyIntegrationTest : IntegrationTest() {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Test
    fun `should create supply and return 201 with payload`() {
        val requestDto = CreateSupplyRequestDTO(
            name = "Parafuso",
            description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            quantity = 3176,
            price = BigDecimal("15.00"),
        )

        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")

        val responseDto: SupplyResponseDTO = mapper.readValue(response.body())
        assertEquals(requestDto.name, responseDto.name)
        assertEquals(requestDto.description, responseDto.description)
        assertEquals(requestDto.price, responseDto.price)
        assertEquals(requestDto.quantity, responseDto.quantity)
    }

    @Test
    fun `should get supply by id`() {
        // Create
        val createRequestDto = CreateSupplyRequestDTO(
            name = "Porca",
            description = "Porca sextavada",
            quantity = 1000,
            price = BigDecimal("0.50"),
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdSupply: SupplyResponseDTO = mapper.readValue(createResponse.body())

        // Get
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies/${createdSupply.id}"))
            .GET()
            .build()
        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, getResponse.statusCode())
        val fetchedSupply: SupplyResponseDTO = mapper.readValue(getResponse.body())
        assertEquals(createdSupply.id, fetchedSupply.id)
        assertEquals(createRequestDto.name, fetchedSupply.name)
        assertEquals(createRequestDto.description, fetchedSupply.description)
        assertEquals(createRequestDto.price, fetchedSupply.price)
        assertEquals(createRequestDto.quantity, fetchedSupply.quantity)
    }

    @Test
    fun `should update supply`() {
        // Create
        val createRequestDto = CreateSupplyRequestDTO(
            name = "Arruela",
            description = "Arruela de pressão",
            quantity = 500,
            price = BigDecimal("0.20"),
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdSupply: SupplyResponseDTO = mapper.readValue(createResponse.body())

        // Update
        val updateRequestDto = CreateSupplyRequestDTO(
            name = "Arruela Updated",
            description = "Arruela de pressão",
            quantity = 600,
            price = BigDecimal("0.25"),
        )
        val updateBody = mapper.writeValueAsString(updateRequestDto)
        val updateRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies/${createdSupply.id}"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(updateBody))
            .build()
        val updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, updateResponse.statusCode())
        val updatedSupply: SupplyResponseDTO = mapper.readValue(updateResponse.body())
        assertEquals(updateRequestDto.name, updatedSupply.name)
        assertEquals(updateRequestDto.description, updatedSupply.description)
        assertEquals(updateRequestDto.price, updatedSupply.price)
        assertEquals(updateRequestDto.quantity, updatedSupply.quantity)
    }

    @Test
    fun `should delete supply`() {
        // Create
        val createRequestDto = CreateSupplyRequestDTO(
            name = "To Delete",
            description = "To be deleted",
            quantity = 10,
            price = BigDecimal("1.00"),
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdSupply: SupplyResponseDTO = mapper.readValue(createResponse.body())

        // Delete
        val deleteRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies/${createdSupply.id}"))
            .DELETE()
            .build()
        val deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(204, deleteResponse.statusCode())

        // Verify Not Found
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/supplies/${createdSupply.id}"))
            .GET()
            .build()
        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, getResponse.statusCode())
    }
}