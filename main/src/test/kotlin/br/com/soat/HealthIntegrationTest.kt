package br.com.soat

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthIntegrationTest : IntegrationTest() {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Test
    fun `should return OK on health endpoint`() {
        val request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://localhost:$serverPort/health"))
            .timeout(Duration.ofSeconds(2))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode(), "HTTP status code must be 200")
    }
}
