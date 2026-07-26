package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.customer.createCustomer
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderSchedule
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.vehicle.createVehicle
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderLifecycleIntegrationTest : IntegrationTest() {

    private val orderRepository: OrderRepository by lazy { get<OrderRepository>() }
    private val orderScheduleRepository: OrderScheduleRepository by lazy { get<OrderScheduleRepository>() }

    @Test
    fun `creates order as RECEIVED with no items and keeps the JWT identity`() {
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)

        val openedById = UUID.randomUUID()
        val bearerToken = attendantHeaders(userId = openedById, document = "12345678909")

        val requestDto = CreateOrderRequestDTO(
            customerId = customer.id,
            vehicleId = vehicle.id,
            description = "Noise in the engine",
        )
        val createOrderResponse = http.createOrder(requestDto, bearerToken)
        assertEquals(201, createOrderResponse.statusCode())

        val createdOrder = orderRepository.findById(createOrderResponse.body().id)!!
        assertEquals(customer.id, createdOrder.customer.id)
        assertEquals(vehicle.id, createdOrder.vehicle.id)
        assertEquals(requestDto.description, createdOrder.description)
        assertEquals(Order.Status.RECEIVED, createdOrder.status)
        assertTrue(createdOrder.services.isEmpty(), "a OS nasce sem itens; eles vêm do diagnóstico")
        assertTrue(createdOrder.supplies.isEmpty(), "a OS nasce sem itens; eles vêm do diagnóstico")
        assertEquals(openedById, createdOrder.openedBy.id)
        assertEquals("12345678909", createdOrder.openedBy.document)
        assertNull(createdOrder.diagnosedBy, "o mecânico só chega com o DiagnoseFinished")

        val status = http.getOrderStatus(createdOrder.id.toString())
        assertEquals(200, status.statusCode())
        assertEquals(createdOrder.id, status.body().id)
        assertEquals(Order.Status.RECEIVED, status.body().status)
        assertNotNull(status.body().modifiedAt)
    }

    @Test
    fun `creates order without any attendant row in the database`() {
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)

        val response = http.createOrder(
            CreateOrderRequestDTO(
                customerId = customer.id,
                vehicleId = vehicle.id,
                description = "No attendant table anymore",
            ),
            attendantHeaders(userId = UUID.randomUUID()),
        )

        assertEquals(201, response.statusCode(), "identidade vem do JWT, não do banco")
    }

    @Test
    fun `schedules vehicle delivery for a RECEIVED order`() {
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val bearerToken = attendantHeaders()

        val order = http.createOrder(
            CreateOrderRequestDTO(
                customerId = customer.id,
                vehicleId = vehicle.id,
                description = "Squeaky brakes",
            ),
            bearerToken,
        ).body()

        val scheduleRequest = OrderScheduleVehicleRequestDTO(
            dateTime = LocalDateTime.now().atZone(UTC).plusHours(2)
        )
        val response = http.scheduleDelivery(order.id.toString(), scheduleRequest, bearerToken)
        assertEquals(200, response.statusCode())

        val schedule = orderScheduleRepository.findAllByOrderId(order.id).single()
        assertEquals(
            scheduleRequest.dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            schedule.dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        assertEquals(OrderSchedule.Type.DELIVERY, schedule.type)
    }
}
