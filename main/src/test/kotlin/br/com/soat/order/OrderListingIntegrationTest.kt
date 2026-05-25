package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.createAttendant
import br.com.soat.customer.createCustomer
import br.com.soat.order.model.Order
import br.com.soat.order.repository.OrderRepository
import br.com.soat.vehicle.createVehicle
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderListingIntegrationTest : IntegrationTest() {

    private val orderRepository: OrderRepository by lazy { get<OrderRepository>() }
    private val mapper = jacksonObjectMapper()

    @Test
    fun `should exclude COMPLETED, DELIVERED and CANCELED orders from listing`() {
        val attendant = createAttendant()
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val bearerToken = adminHeaders()

        val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0)

        // Create orders in each status
        val receivedOrder = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, attendant.id, baseTime)
        val inDiagnosisOrder = createOrderWithStatus(Order.Status.IN_DIAGNOSIS, customer.id, vehicle.id, attendant.id, baseTime.plusHours(1))
        val waitingApprovalOrder = createOrderWithStatus(Order.Status.WAITING_APPROVAL, customer.id, vehicle.id, attendant.id, baseTime.plusHours(2))
        val inProgressOrder = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, attendant.id, baseTime.plusHours(3))
        createOrderWithStatus(Order.Status.COMPLETED, customer.id, vehicle.id, attendant.id, baseTime.plusHours(4))
        createOrderWithStatus(Order.Status.DELIVERED, customer.id, vehicle.id, attendant.id, baseTime.plusHours(5))
        createOrderWithStatus(Order.Status.CANCELED, customer.id, vehicle.id, attendant.id, baseTime.plusHours(6))

        val response = http.listOrders(bearerToken)
        assertEquals(200, response.statusCode())

        val body = mapper.readValue<Map<String, Any>>(response.body())
        val content = body["content"] as List<*>

        // Should only have 4 orders (RECEIVED, IN_DIAGNOSIS, WAITING_APPROVAL, IN_PROGRESS)
        assertEquals(4, content.size)

        val orderIds = content.map { (it as Map<*, *>)["id"] as String }
        assertEquals(
            listOf(
                inProgressOrder.id.toString(),
                waitingApprovalOrder.id.toString(),
                inDiagnosisOrder.id.toString(),
                receivedOrder.id.toString()
            ),
            orderIds
        )
    }

    @Test
    fun `should sort by status priority and then by creation date ascending`() {
        val attendant = createAttendant()
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val bearerToken = adminHeaders()

        val baseTime = LocalDateTime.of(2024, 1, 1, 10, 0)

        // Create multiple orders with same status but different dates
        val received1 = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, attendant.id, baseTime.plusHours(2))
        val received2 = createOrderWithStatus(Order.Status.RECEIVED, customer.id, vehicle.id, attendant.id, baseTime.plusHours(1))
        val inProgress1 = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, attendant.id, baseTime.plusHours(4))
        val inProgress2 = createOrderWithStatus(Order.Status.IN_PROGRESS, customer.id, vehicle.id, attendant.id, baseTime.plusHours(3))
        val waitingApproval1 = createOrderWithStatus(Order.Status.WAITING_APPROVAL, customer.id, vehicle.id, attendant.id, baseTime)

        val response = http.listOrders(bearerToken)
        assertEquals(200, response.statusCode())

        val body = mapper.readValue<Map<String, Any>>(response.body())
        val content = body["content"] as List<*>

        assertEquals(5, content.size)

        val orderIds = content.map { (it as Map<*, *>)["id"] as String }

        // Expected order: IN_PROGRESS (oldest first), WAITING_APPROVAL, RECEIVED (oldest first)
        assertEquals(
            listOf(
                inProgress2.id.toString(),   // IN_PROGRESS, earlier
                inProgress1.id.toString(),   // IN_PROGRESS, later
                waitingApproval1.id.toString(), // WAITING_APPROVAL
                received2.id.toString(),     // RECEIVED, earlier
                received1.id.toString()      // RECEIVED, later
            ),
            orderIds
        )
    }

    private fun createOrderWithStatus(
        status: Order.Status,
        customerId: java.util.UUID,
        vehicleId: java.util.UUID,
        attendantId: java.util.UUID,
        createdAt: LocalDateTime
    ): Order {
        val customer = get<br.com.soat.customer.CustomerRepository>().findById(customerId)!!
        val vehicle = get<br.com.soat.vehicle.VehicleRepository>().findById(vehicleId)!!

        val order = Order(
            createdAt = createdAt,
            modifiedAt = createdAt,
            customer = customer,
            vehicle = vehicle,
            attendantId = attendantId,
            description = "Order in status $status",
            status = status,
            technician = if (status != Order.Status.RECEIVED) "Tech" else null
        )

        return orderRepository.create(order)
    }
}
