package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.attendant.createAttendant
import br.com.soat.customer.createCustomer
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.createVehicle
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrderMetricsIntegrationTest : IntegrationTest() {

    private val orderExecutionMetricRepository: OrderExecutionMetricRepository by lazy {
        get<OrderExecutionMetricRepository>()
    }

    private val orderRepository: OrderRepository by lazy {
        get<OrderRepository>()
    }

    private val counter = AtomicInteger(0)

    @Test
    fun `should return zero metrics when no completed orders exist`() {
        val bearerToken = adminHeaders()

        val response = http.getOrderMetrics(bearerToken)

        assertEquals(200, response.statusCode())
        assertEquals(0, response.body().totalCompleted)
        assertNull(response.body().averageExecutionTimeSeconds)
    }

    @Test
    fun `should calculate average execution time correctly`() {
        val bearerToken = adminHeaders()

        val order1Id = createOrderInDatabase()
        val order2Id = createOrderInDatabase()
        val order3Id = createOrderInDatabase()

        val metric1 = OrderExecutionMetric(
            id = UUID.randomUUID(),
            orderId = order1Id,
            inProgressAt = LocalDateTime.of(2024, 1, 1, 10, 0, 0),
            completedAt = LocalDateTime.of(2024, 1, 1, 10, 1, 40) // 100 seconds later
        )

        val metric2 = OrderExecutionMetric(
            id = UUID.randomUUID(),
            orderId = order2Id,
            inProgressAt = LocalDateTime.of(2024, 1, 2, 10, 0, 0),
            completedAt = LocalDateTime.of(2024, 1, 2, 10, 3, 20) // 200 seconds later
        )

        // Order 3: 300 seconds
        val metric3 = OrderExecutionMetric(
            id = UUID.randomUUID(),
            orderId = order3Id,
            inProgressAt = LocalDateTime.of(2024, 1, 3, 10, 0, 0),
            completedAt = LocalDateTime.of(2024, 1, 3, 10, 5, 0) // 300 seconds later
        )

        orderExecutionMetricRepository.create(metric1)
        orderExecutionMetricRepository.create(metric2)
        orderExecutionMetricRepository.create(metric3)

        val response = http.getOrderMetrics(bearerToken)

        assertEquals(200, response.statusCode())
        assertEquals(3, response.body().totalCompleted)
        // Average: (100 + 200 + 300) / 3 = 200 seconds
        assertEquals(200, response.body().averageExecutionTimeSeconds)
    }

    @Test
    fun `should not count orders that are not completed`() {
        val bearerToken = adminHeaders()

        val order1Id = createOrderInDatabase()
        val order2Id = createOrderInDatabase()

        val completedMetric = OrderExecutionMetric(
            id = UUID.randomUUID(),
            orderId = order1Id,
            inProgressAt = LocalDateTime.of(2024, 1, 1, 10, 0, 0),
            completedAt = LocalDateTime.of(2024, 1, 1, 10, 1, 40)
        )

        val inProgressMetric = OrderExecutionMetric(
            id = UUID.randomUUID(),
            orderId = order2Id,
            inProgressAt = LocalDateTime.of(2024, 1, 2, 10, 0, 0),
            completedAt = null
        )

        orderExecutionMetricRepository.create(completedMetric)
        orderExecutionMetricRepository.create(inProgressMetric)

        val response = http.getOrderMetrics(bearerToken)

        assertEquals(200, response.statusCode())
        assertEquals(1, response.body().totalCompleted) // Only 1 completed
        assertEquals(100, response.body().averageExecutionTimeSeconds)
    }

    private fun createOrderInDatabase(): UUID {
        val count = counter.incrementAndGet()
        val attendantDoc = count.toString().padStart(11, '0')
        val customerCpf = (count + 1000).toString().padStart(11, '0')

        val attendant = createAttendant(
            document = attendantDoc,
            email = "attendant$count@test.com"
        )
        val customer = createCustomer(
            document = Document(customerCpf),
            email = Email("customer$count@test.com")
        )
        val plateNumber = count.toString().padStart(4, '0')
        val vehicle = createVehicle(customer.id, plate = VehiclePlate("AAA$plateNumber"))

        val order = orderRepository.create(
            Order(
                customer = customer,
                vehicle = vehicle,
                attendantId = attendant.id,
                description = "Test order for metrics"
            )
        )

        return order.id
    }
}
