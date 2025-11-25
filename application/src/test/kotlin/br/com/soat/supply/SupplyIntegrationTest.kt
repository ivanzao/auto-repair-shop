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
}