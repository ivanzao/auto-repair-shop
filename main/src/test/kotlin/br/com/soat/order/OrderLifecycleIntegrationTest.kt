package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.customer.createCustomer
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.FinishOrderDiagnosisRequestDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.StartOrderDiagnosisRequestDTO
import br.com.soat.supply.createSupply
import br.com.soat.supply.model.SupplyRequest
import br.com.soat.user.createUser
import br.com.soat.vehicle.createVehicle
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class OrderLifecycleIntegrationTest : IntegrationTest() {

    private val logger = LoggerFactory.getLogger(OrderLifecycleIntegrationTest::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @Test
    fun `should complete full order lifecycle`() {
        val attendant = createUser()
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val supply = createSupply(quantityInStock = 10)
        val service = createService(requiredSupplies = listOf(SupplyRequest(supply.id, 5)))

        val createOrderRequest = CreateOrderRequestDTO(
            customerId = customer.id,
            vehicleId = vehicle.id,
            description = "Noise in the engine",
            attendantId = attendant.id
        )

        val order = callCreateOrder(createOrderRequest)

        val startDiagnosisRequest = StartOrderDiagnosisRequestDTO(technician = "Tech Mike")
        val orderInDiagnosis = callStartDiagnosis(order.id.toString(), startDiagnosisRequest)

        assertEquals("IN_DIAGNOSIS", orderInDiagnosis.status.name)

        val finishDiagnosisRequest = FinishOrderDiagnosisRequestDTO(
            servicesIds = listOf(service.id),
            extraSuppliesRequests = emptyList()
        )
        val orderDiagnosed = callFinishDiagnosis(order.id.toString(), finishDiagnosisRequest)

        waitForOrderStatus(orderDiagnosed.id.toString(), "WAITING_APPROVAL")
    }

    private fun waitForOrderStatus(
        orderId: String,
        expectedStatus: String
    ) {
        val maxRetries = 20
        val sleepMs = 2000L

        repeat(maxRetries) { _ ->
            val status = getOrder(orderId).status.name
            if (status == expectedStatus) {
                logger.info("Order reached expected status: $expectedStatus")
                return
            }

            Thread.sleep(sleepMs)
        }

        error("Order $orderId did not reach expected status: $expectedStatus after $maxRetries retries")
    }

    private fun getOrder(orderId: String): OrderResponseDTO {
        val response = get("/orders/$orderId")
        assertEquals(200, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun callCreateOrder(dto: CreateOrderRequestDTO): OrderResponseDTO {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders", body)
        assertEquals(201, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun callStartDiagnosis(orderId: String, dto: StartOrderDiagnosisRequestDTO): OrderResponseDTO {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders/$orderId/start-diagnosis", body)
        assertEquals(200, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun callFinishDiagnosis(orderId: String, dto: FinishOrderDiagnosisRequestDTO): OrderResponseDTO {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders/$orderId/finish-diagnosis", body)
        assertEquals(200, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun post(path: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
