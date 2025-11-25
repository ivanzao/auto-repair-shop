package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.FinishOrderDiagnosisRequestDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.StartOrderDiagnosisRequestDTO
import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.supply.dto.SupplyResponseDTO
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import br.com.soat.user.model.User
import br.com.soat.vehicle.dto.CreateVehicleRequestDTO
import br.com.soat.vehicle.dto.VehicleResponseDTO
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class OrderLifecycleIntegrationTest : IntegrationTest() {

    private val logger = LoggerFactory.getLogger(OrderLifecycleIntegrationTest::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @Test
    fun `should complete full order lifecycle`() {
        try {
            logger.info("=== Starting Order Lifecycle Test ===")
            
            // 1. Setup Dependencies (User, Customer, Vehicle, Supply, Service)
            logger.info("Step 1: Creating attendant user")
            val attendant = createUser("Attendant John", "12345678900", "john@test.com", "123456789", User.Role.ATTENDANT)
            logger.info("✓ Attendant created: ${attendant.id}")
            
            logger.info("Step 2: Creating customer")
            val customer = createCustomer("Customer Jane", "98765432100")
            logger.info("✓ Customer created: ${customer.id}")
            
            logger.info("Step 3: Creating vehicle")
            val vehicle = createVehicle(customer.id, "ABC-1234")
            logger.info("✓ Vehicle created: ${vehicle.id}")
            
            logger.info("Step 4: Creating supply")
            val supplyOil = createSupply("Engine Oil", BigDecimal("50.00"), 10)
            logger.info("✓ Supply created")
            
            // Create Service via SQL
            logger.info("Step 5: Creating service via SQL")
            val serviceId = UUID.randomUUID()
            executeSql("INSERT INTO services (id, name, description, price, created_at, modified_at, version) VALUES ('$serviceId', 'Oil Change', 'Full oil change', 150.00, now(), now(), 0)")
            logger.info("✓ Service created: $serviceId")

            // 2. Create Order
            logger.info("Step 6: Creating order")
            val createOrderRequest = CreateOrderRequestDTO(
                customerId = UUID.fromString(customer.id),
                vehicleId = UUID.fromString(vehicle.id),
                description = "Noise in the engine",
                serviceIds = emptyList(),
                attendantId = UUID.fromString(attendant.id)
            )
            val order = createOrder(createOrderRequest)
            logger.info("✓ Order created: ${order.id}, status: ${order.status.name}")
            assertEquals("RECEIVED", order.status.name)

            // 3. Start Diagnosis
            logger.info("Step 7: Starting diagnosis")
            val startDiagnosisRequest = StartOrderDiagnosisRequestDTO(technician = "Tech Mike")
            val orderInDiagnosis = startDiagnosis(order.id.toString(), startDiagnosisRequest)
            logger.info("✓ Diagnosis started, status: ${orderInDiagnosis.status.name}")
            assertEquals("IN_DIAGNOSIS", orderInDiagnosis.status.name)

            // 4. Finish Diagnosis
            logger.info("Step 8: Finishing diagnosis")
            val finishDiagnosisRequest = FinishOrderDiagnosisRequestDTO(
                servicesIds = listOf(serviceId),
                extraSuppliesRequests = emptyList()
            )
            val orderDiagnosed = finishDiagnosis(order.id.toString(), finishDiagnosisRequest)
            logger.info("✓ Diagnosis finished, status: ${orderDiagnosed.status.name}")
            
            // Check if event was created
            logger.info("Step 9: Checking if events were created")
            Thread.sleep(2000) // Give time for transaction to commit
            val eventCount = countEvents()
            logger.info("Events in database: $eventCount")
            if (eventCount == 0) {
                logger.warn("No events found in database!")
            } else {
                logger.info("Listing all events:")
                listAllEvents()
            }
            assertTrue(eventCount > 0, "Expected at least one event to be created but found $eventCount")
            
            // 5. Wait for Async Processing (Worker)
            logger.info("Step 10: Waiting for order to reach WAITING_APPROVAL status")
            waitForOrderStatus(order.id.toString(), "WAITING_APPROVAL")
            logger.info("=== Test Completed Successfully ===")
        } catch (e: Exception) {
            logger.error("!!! Test failed with exception: ${e.message}", e)
            throw e
        }
    }

    private fun waitForOrderStatus(
        orderId: String,
        expectedStatus: String
    ) {
        val maxRetries = 20
        val sleepMs = 2000L

        repeat(maxRetries) { attempt ->
            val status = runBlocking { getOrder(orderId).status.name }
            val eventCount = countEvents()

            logger.info(
                "Retry $attempt: Order $orderId status: $status, events in DB: $eventCount"
            )

            if (status == expectedStatus) {
                logger.info("Order reached expected status: $expectedStatus")
                return
            }

            Thread.sleep(sleepMs)
        }

        error("Order $orderId did not reach expected status: $expectedStatus after $maxRetries retries")
    }
    
    private fun countEvents(): Int {
        var count = 0
        java.sql.DriverManager.getConnection(postgresContainer.jdbcUrl, postgresContainer.username, postgresContainer.password).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM events")
                if (rs.next()) {
                    count = rs.getInt(1)
                }
            }
        }
        return count
    }
    
    private fun listAllEvents() {
        java.sql.DriverManager.getConnection(postgresContainer.jdbcUrl, postgresContainer.username, postgresContainer.password).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, type, status, created_at FROM events ORDER BY created_at")
                while (rs.next()) {
                    val id = rs.getString("id")
                    val type = rs.getString("type")
                    val status = rs.getString("status")
                    val createdAt = rs.getTimestamp("created_at")
                    logger.info("  Event: id=$id, type=$type, status='$status', created_at=$createdAt")
                }
            }
        }
    }

    private fun getOrder(orderId: String): OrderResponseDTO {
        val response = get("/orders/$orderId")
        assertEquals(200, response.statusCode())
        return mapper.readValue(response.body())
    }
    
    private fun createUser(name: String, document: String, email: String, contact: String, role: User.Role): UserResponseDTO {
        val body = mapper.writeValueAsString(CreateUserRequestDTO(name, document, email, contact, role))
        val response = post("/users", body)
        assertEquals(201, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun createCustomer(name: String, document: String): CustomerResponseDTO {
        val body = mapper.writeValueAsString(CreateCustomerRequestDTO(name, document, "email@test.com", "123456789"))
        val response = post("/customers", body)
        assertEquals(201, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun createVehicle(customerId: String, plate: String): VehicleResponseDTO {
        val body = mapper.writeValueAsString(CreateVehicleRequestDTO(UUID.fromString(customerId), plate, "Brand", "Model", 2024))
        val response = post("/vehicles", body)
        assertEquals(201, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun createSupply(name: String, price: BigDecimal, quantity: Int): SupplyResponseDTO {
        val body = mapper.writeValueAsString(CreateSupplyRequestDTO(name, "desc", quantity, price))
        val response = post("/supplies", body)
        assertEquals(201, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun createOrder(dto: CreateOrderRequestDTO): OrderResponseDTO {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders", body)
        assertEquals(201, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun startDiagnosis(orderId: String, dto: StartOrderDiagnosisRequestDTO): OrderResponseDTO {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders/$orderId/start-diagnosis", body)
        assertEquals(200, response.statusCode())
        return mapper.readValue(response.body())
    }

    private fun finishDiagnosis(orderId: String, dto: FinishOrderDiagnosisRequestDTO): OrderResponseDTO {
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
    
    private fun executeSql(sql: String) {
        java.sql.DriverManager.getConnection(postgresContainer.jdbcUrl, postgresContainer.username, postgresContainer.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
    }
}
