package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.customer.createCustomer
import br.com.soat.order.model.Order
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.model.User
import br.com.soat.vehicle.createVehicle
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderListingIntegrationTest : IntegrationTest() {

    private val orderRepository: OrderRepository by lazy { get<OrderRepository>() }
    private val mapper = jacksonObjectMapper()

    @Test
    fun `should exclude COMPLETED, DELIVERED and CANCELED orders from listing`() {
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val bearerToken = adminHeaders()

        val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0)

        val receivedOrder = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, baseTime)
        val inProgressOrder = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, baseTime.plusHours(3))
        createOrderWithStatus(Order.Status.COMPLETED, customer.id, vehicle.id, baseTime.plusHours(4))
        createOrderWithStatus(Order.Status.DELIVERED, customer.id, vehicle.id, baseTime.plusHours(5))
        createOrderWithStatus(Order.Status.CANCELED, customer.id, vehicle.id, baseTime.plusHours(6))

        val response = http.listOrders(bearerToken)
        assertEquals(200, response.statusCode())

        val body = mapper.readValue<Map<String, Any>>(response.body())
        val content = body["content"] as List<*>

        assertEquals(2, content.size)

        val orderIds = content.map { (it as Map<*, *>)["id"] as String }
        assertEquals(
            listOf(
                inProgressOrder.id.toString(),
                receivedOrder.id.toString()
            ),
            orderIds
        )
    }

    @Test
    fun `should sort by status priority and then by creation date ascending`() {
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val bearerToken = adminHeaders()

        val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0)

        val received1 = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, baseTime.plusHours(2))
        val received2 = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, baseTime.plusHours(1))
        val inProgress1 = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, baseTime.plusHours(4))
        val inProgress2 = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, baseTime.plusHours(3))

        val response = http.listOrders(bearerToken)
        assertEquals(200, response.statusCode())

        val body = mapper.readValue<Map<String, Any>>(response.body())
        val content = body["content"] as List<*>

        assertEquals(4, content.size)

        val orderIds = content.map { (it as Map<*, *>)["id"] as String }

        assertEquals(
            listOf(
                inProgress2.id.toString(),
                inProgress1.id.toString(),
                received2.id.toString(),
                received1.id.toString()
            ),
            orderIds
        )
    }

    private fun createOrderWithStatus(
        status: Order.Status,
        customerId: UUID,
        vehicleId: UUID,
        createdAt: LocalDateTime
    ): Order {
        val customer = get<br.com.soat.customer.repository.CustomerRepository>().findById(customerId)!!
        val vehicle = get<br.com.soat.vehicle.repository.VehicleRepository>().findById(vehicleId)!!

        val order = Order(
            createdAt = createdAt,
            modifiedAt = createdAt,
            customer = customer,
            vehicle = vehicle,
            openedBy = User(UUID.randomUUID(), "12345678909"),
            description = "Order in status $status",
            status = status
        )

        return orderRepository.create(order)
    }

    @Test
    fun `should rank the saga statuses from IN_PROGRESS down to RECEIVED`() {
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val bearerToken = adminHeaders()

        val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0)

        val received = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, baseTime)
        val waitingApproval = createOrderWithStatus(Order.Status.WAITING_APPROVAL, customer.id, vehicle.id, baseTime)
        val enqueued = createOrderWithStatus(Order.Status.EXECUTION_ENQUEUED, customer.id, vehicle.id, baseTime)
        val inProgress = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, baseTime)

        val response = http.listOrders(bearerToken)
        assertEquals(200, response.statusCode())

        val body = mapper.readValue<Map<String, Any>>(response.body())
        val content = body["content"] as List<*>
        val orderIds = content.map { (it as Map<*, *>)["id"] as String }

        assertEquals(
            listOf(
                inProgress.id.toString(),
                enqueued.id.toString(),
                waitingApproval.id.toString(),
                received.id.toString(),
            ),
            orderIds
        )
    }
}
