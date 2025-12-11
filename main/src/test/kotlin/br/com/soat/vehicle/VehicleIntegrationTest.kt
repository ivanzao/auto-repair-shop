package br.com.soat.vehicle

import br.com.soat.IntegrationTest
import br.com.soat.vehicle.dto.CreateVehicleRequestDTO
import br.com.soat.vehicle.dto.VehicleResponseDTO
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VehicleIntegrationTest : IntegrationTest() {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    private fun createCustomer(): UUID {
        val requestDto = mapOf(
            "name" to "Test Customer",
            "document" to Random.nextLong(10000000000L, 99999999999L).toString(),
            "email" to "test@example.com",
            "contact" to "+55 11 99999-9999"
        )
        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/customers"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        @Suppress("UNCHECKED_CAST")
        val responseDto = mapper.readValue(response.body(), Map::class.java) as Map<String, Any>
        return UUID.fromString(responseDto["id"] as String)
    }

    @Test
    fun `should create vehicle and return 201 with payload`() {
        val clientId = createCustomer()
        val requestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "ABC-1234",
            brand = "Toyota",
            model = "Corolla",
            year = 2024
        )

        val body = mapper.writeValueAsString(requestDto)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, response.statusCode(), "HTTP status code must be 201 Created")

        val responseDto: VehicleResponseDTO = mapper.readValue(response.body())
        assertEquals(requestDto.plate, responseDto.plate)
        assertEquals(requestDto.brand, responseDto.brand)
        assertEquals(requestDto.model, responseDto.model)
        assertEquals(requestDto.year, responseDto.year)
        assertEquals(requestDto.clientId.toString(), responseDto.clientId)
    }

    @Test
    fun `should get vehicle by id`() {
        // Create
        val clientId = createCustomer()
        val createRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "XYZ-5678",
            brand = "Honda",
            model = "Civic",
            year = 2023
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdVehicle: VehicleResponseDTO = mapper.readValue(createResponse.body())

        // Get
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles/${createdVehicle.id}"))
            .GET()
            .build()
        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(200, getResponse.statusCode())
        val fetchedVehicle: VehicleResponseDTO = mapper.readValue(getResponse.body())
        assertEquals(createdVehicle.id, fetchedVehicle.id)
        assertEquals(createRequestDto.plate, fetchedVehicle.plate)
        assertEquals(createRequestDto.brand, fetchedVehicle.brand)
        assertEquals(createRequestDto.model, fetchedVehicle.model)
        assertEquals(createRequestDto.year, fetchedVehicle.year)
        assertEquals(createRequestDto.clientId.toString(), fetchedVehicle.clientId)
    }

    @Test
    fun `should update vehicle`() {
        // Create
        val clientId = createCustomer()
        val createRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "DEF-9012",
            brand = "Ford",
            model = "Focus",
            year = 2022
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdVehicle: VehicleResponseDTO = mapper.readValue(createResponse.body())

        // Update
        val updateRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "DEF-9012",
            brand = "Ford",
            model = "Focus Updated",
            year = 2022
        )
        val updateBody = mapper.writeValueAsString(updateRequestDto)
        val updateRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles/${createdVehicle.id}"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(updateBody))
            .build()
        val updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, updateResponse.statusCode())
        val updatedVehicle: VehicleResponseDTO = mapper.readValue(updateResponse.body())
        assertEquals(updateRequestDto.plate, updatedVehicle.plate)
        assertEquals(updateRequestDto.brand, updatedVehicle.brand)
        assertEquals(updateRequestDto.model, updatedVehicle.model)
        assertEquals(updateRequestDto.year, updatedVehicle.year)
        assertEquals(updateRequestDto.clientId.toString(), updatedVehicle.clientId)
    }

    @Test
    fun `should delete vehicle`() {
        // Create
        val clientId = createCustomer()
        val createRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "DEL-0000",
            brand = "Delete",
            model = "Me",
            year = 2000
        )
        val createBody = mapper.writeValueAsString(createRequestDto)
        val createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody))
            .build()
        val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
        val createdVehicle: VehicleResponseDTO = mapper.readValue(createResponse.body())

        // Delete
        val deleteRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles/${createdVehicle.id}"))
            .DELETE()
            .build()
        val deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString())
        
        assertEquals(204, deleteResponse.statusCode())

        // Verify Not Found
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/vehicles/${createdVehicle.id}"))
            .GET()
            .build()
        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, getResponse.statusCode())
    }
}
