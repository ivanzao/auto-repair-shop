package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.order.exception.IllegalOrderStateException
import br.com.soat.service.model.Service
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.shared.model.SupplyRequirement
import br.com.soat.vehicle.model.Vehicle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID

class OrderTest {

    private val customer = Customer(
        name = "John Doe",
        document = Document("12345678909"),
        email = Email("john@example.com"),
        contact = PhoneNumber("11999999999")
    )

    private val vehicle = Vehicle(
        clientId = customer.id,
        plate = VehiclePlate("ABC1234"),
        brand = "Toyota",
        model = "Corolla",
        year = 2024
    )

    private val attendantId = UUID.randomUUID()

    private fun createOrder(status: Order.Status = Order.Status.RECEIVED) = Order(
        customer = customer,
        vehicle = vehicle,
        attendantId = attendantId,
        description = "Test order",
        status = status
    )

    @Test
    fun `inProgress moves RECEIVED to IN_PROGRESS`() {
        assertEquals(Order.Status.IN_PROGRESS, createOrder(Order.Status.RECEIVED).inProgress().status)
    }

    @Test
    fun `inProgress is a no-op outside RECEIVED`() {
        listOf(Order.Status.IN_PROGRESS, Order.Status.COMPLETED, Order.Status.DELIVERED, Order.Status.CANCELED)
            .forEach { assertEquals(it, createOrder(it).inProgress().status) }
    }

    @Test
    fun `completed moves IN_PROGRESS to COMPLETED`() {
        assertEquals(Order.Status.COMPLETED, createOrder(Order.Status.IN_PROGRESS).completed().status)
    }

    @Test
    fun `completed is a no-op outside IN_PROGRESS`() {
        listOf(Order.Status.RECEIVED, Order.Status.COMPLETED, Order.Status.DELIVERED, Order.Status.CANCELED)
            .forEach { assertEquals(it, createOrder(it).completed().status) }
    }

    @Test
    fun `canceled cancels a non-terminal order`() {
        listOf(Order.Status.RECEIVED, Order.Status.IN_PROGRESS)
            .forEach { assertEquals(Order.Status.CANCELED, createOrder(it).canceled().status) }
    }

    @Test
    fun `canceled is a no-op on terminal states`() {
        listOf(Order.Status.COMPLETED, Order.Status.DELIVERED, Order.Status.CANCELED)
            .forEach { assertEquals(it, createOrder(it).canceled().status) }
    }

    @Test
    fun `delivered moves COMPLETED to DELIVERED`() {
        assertEquals(Order.Status.DELIVERED, createOrder(Order.Status.COMPLETED).delivered().status)
    }

    @Test
    fun `delivered throws outside COMPLETED`() {
        listOf(Order.Status.RECEIVED, Order.Status.IN_PROGRESS, Order.Status.DELIVERED, Order.Status.CANCELED)
            .forEach { state ->
                val ex = assertThrows<IllegalOrderStateException> { createOrder(state).delivered() }
                assertEquals("Order must be COMPLETED. Current status: $state", ex.message)
            }
    }

    @Test
    fun `transitions preserve immutability`() {
        val original = createOrder(Order.Status.RECEIVED)
        val next = original.inProgress()
        assertEquals(Order.Status.RECEIVED, original.status)
        assertEquals(Order.Status.IN_PROGRESS, next.status)
    }

    @Test
    fun `should add services to order`() {
        val order = createOrder()
        val services = listOf(
            Service(name = "Oil Change", description = null, price = BigDecimal("50.00")),
            Service(name = "Brake Inspection", description = null, price = BigDecimal("30.00"))
        )

        val result = order.addServices(services)

        assertEquals(2, result.services.size)
        assertEquals("Oil Change", result.services[0].name)
        assertEquals("Brake Inspection", result.services[1].name)
    }

    @Test
    fun `should append services to existing ones`() {
        val initialService = Service(name = "Initial Service", description = null, price = BigDecimal("10.00"))
        val order = createOrder().copy(services = listOf(initialService))
        val newServices = listOf(Service(name = "New Service", description = null, price = BigDecimal("20.00")))

        val result = order.addServices(newServices)

        assertEquals(2, result.services.size)
        assertEquals("Initial Service", result.services[0].name)
        assertEquals("New Service", result.services[1].name)
    }

    @Test
    fun `should preserve immutability when adding services`() {
        val order = createOrder()
        val result = order.addServices(listOf(Service(name = "Service", description = null, price = BigDecimal("10.00"))))
        assertTrue(order.services.isEmpty())
        assertEquals(1, result.services.size)
    }

    @Test
    fun `should add supply requirements to order`() {
        val supplyId = UUID.randomUUID()
        val result = createOrder().addSupplyRequirements(listOf(SupplyRequirement(supplyId, 5)))
        assertEquals(1, result.extraSupplies.size)
        assertEquals(supplyId, result.extraSupplies[0].supplyId)
        assertEquals(5, result.extraSupplies[0].quantity)
    }

    @Test
    fun `should consolidate supply requirements with same supplyId`() {
        val supplyId = UUID.randomUUID()
        val order = createOrder().copy(extraSupplies = listOf(SupplyRequirement(supplyId, 3)))
        val result = order.addSupplyRequirements(listOf(SupplyRequirement(supplyId, 5)))
        assertEquals(1, result.extraSupplies.size)
        assertEquals(8, result.extraSupplies[0].quantity)
    }

    @Test
    fun `getSupplyRequirements consolidates service and extra supplies`() {
        val supplyId = UUID.randomUUID()
        val other = UUID.randomUUID()
        val service = Service(
            name = "Service", description = null, price = BigDecimal("10.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId, 4)),
        )
        val order = createOrder().copy(
            services = listOf(service),
            extraSupplies = listOf(SupplyRequirement(supplyId, 6), SupplyRequirement(other, 1)),
        )

        val result = order.getSupplyRequirements().associateBy { it.supplyId }

        assertEquals(10, result[supplyId]?.quantity)
        assertEquals(1, result[other]?.quantity)
    }

    @Test
    fun `canScheduleVehicleDelivery only when RECEIVED`() {
        assertTrue(createOrder(Order.Status.RECEIVED).canScheduleVehicleDelivery())
        listOf(Order.Status.IN_PROGRESS, Order.Status.COMPLETED, Order.Status.DELIVERED, Order.Status.CANCELED)
            .forEach { assertFalse(createOrder(it).canScheduleVehicleDelivery()) }
    }

    @Test
    fun `canScheduleVehicleReturn only when COMPLETED`() {
        assertTrue(createOrder(Order.Status.COMPLETED).canScheduleVehicleReturn())
        listOf(Order.Status.RECEIVED, Order.Status.IN_PROGRESS, Order.Status.DELIVERED, Order.Status.CANCELED)
            .forEach { assertFalse(createOrder(it).canScheduleVehicleReturn()) }
    }

    @Test
    fun `new order defaults to RECEIVED with no technician`() {
        val order = createOrder()
        assertEquals(Order.Status.RECEIVED, order.status)
        assertNull(order.technician)
    }
}
