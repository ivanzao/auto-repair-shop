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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VehicleIntegrationTest : IntegrationTest() {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Test
    fun `should create vehicle and return 201 with payload`() {
        val clientId = UUID.randomUUID()
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
}
