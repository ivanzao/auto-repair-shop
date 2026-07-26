package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.order.exception.IllegalOrderStateException
import br.com.soat.shared.model.User
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

    private val openedBy = User(UUID.randomUUID(), "12345678909")
    private val diagnosedBy = User(UUID.fromString("00000000-0000-0000-0000-000000000003"), "12345678909")

    private val quotedService = QuotedService(UUID.randomUUID(), "Troca de oleo", BigDecimal("100.00"))
    private val quotedSupply = QuotedSupply(UUID.randomUUID(), "Filtro de oleo", 2, BigDecimal("30.00"))

    private val nonTerminal = listOf(
        Order.Status.RECEIVED,
        Order.Status.WAITING_APPROVAL,
        Order.Status.EXECUTION_ENQUEUED,
        Order.Status.IN_PROGRESS,
    )

    private val terminal = listOf(Order.Status.COMPLETED, Order.Status.DELIVERED, Order.Status.CANCELED)

    private fun createOrder(status: Order.Status = Order.Status.RECEIVED) = Order(
        customer = customer,
        vehicle = vehicle,
        openedBy = openedBy,
        description = "Test order",
        status = status
    )

    @Test
    fun `awaitingApproval moves RECEIVED to WAITING_APPROVAL and stores the priced snapshot`() {
        val result = createOrder(Order.Status.RECEIVED)
            .awaitingApproval(diagnosedBy, listOf(quotedService), listOf(quotedSupply))

        assertEquals(Order.Status.WAITING_APPROVAL, result.status)
        assertEquals(listOf(quotedService), result.services)
        assertEquals(listOf(quotedSupply), result.supplies)
    }

    @Test
    fun `awaitingApproval records who diagnosed the order`() {
        val before = createOrder(Order.Status.RECEIVED)
        assertNull(before.diagnosedBy, "a OS nasce sem diagnóstico")

        val result = before.awaitingApproval(diagnosedBy, listOf(quotedService), listOf(quotedSupply))

        assertEquals(diagnosedBy, result.diagnosedBy)
    }

    @Test
    fun `awaitingApproval is a no-op outside RECEIVED`() {
        (nonTerminal - Order.Status.RECEIVED + terminal).forEach { state ->
            val result = createOrder(state)
                .awaitingApproval(diagnosedBy, listOf(quotedService), listOf(quotedSupply))
            assertEquals(state, result.status)
            assertTrue(result.services.isEmpty())
            assertTrue(result.supplies.isEmpty())
            assertNull(result.diagnosedBy)
        }
    }

    @Test
    fun `awaitingApproval preserves immutability`() {
        val original = createOrder(Order.Status.RECEIVED)
        val next = original.awaitingApproval(diagnosedBy, listOf(quotedService), listOf(quotedSupply))
        assertTrue(original.services.isEmpty())
        assertNull(original.diagnosedBy)
        assertEquals(1, next.services.size)
    }

    @Test
    fun `executionEnqueued moves WAITING_APPROVAL to EXECUTION_ENQUEUED`() {
        assertEquals(
            Order.Status.EXECUTION_ENQUEUED,
            createOrder(Order.Status.WAITING_APPROVAL).executionEnqueued().status,
        )
    }

    @Test
    fun `executionEnqueued is a no-op outside WAITING_APPROVAL`() {
        (nonTerminal - Order.Status.WAITING_APPROVAL + terminal)
            .forEach { assertEquals(it, createOrder(it).executionEnqueued().status) }
    }

    @Test
    fun `inProgress moves EXECUTION_ENQUEUED to IN_PROGRESS`() {
        assertEquals(Order.Status.IN_PROGRESS, createOrder(Order.Status.EXECUTION_ENQUEUED).inProgress().status)
    }

    @Test
    fun `inProgress is a no-op outside EXECUTION_ENQUEUED`() {
        (nonTerminal - Order.Status.EXECUTION_ENQUEUED + terminal)
            .forEach { assertEquals(it, createOrder(it).inProgress().status) }
    }

    @Test
    fun `completed moves IN_PROGRESS to COMPLETED`() {
        assertEquals(Order.Status.COMPLETED, createOrder(Order.Status.IN_PROGRESS).completed().status)
    }

    @Test
    fun `completed is a no-op outside IN_PROGRESS`() {
        (nonTerminal - Order.Status.IN_PROGRESS + terminal)
            .forEach { assertEquals(it, createOrder(it).completed().status) }
    }

    @Test
    fun `canceled cancels any non-terminal order`() {
        nonTerminal.forEach { assertEquals(Order.Status.CANCELED, createOrder(it).canceled().status) }
    }

    @Test
    fun `canceled is a no-op on terminal states`() {
        terminal.forEach { assertEquals(it, createOrder(it).canceled().status) }
    }

    @Test
    fun `delivered moves COMPLETED to DELIVERED`() {
        assertEquals(Order.Status.DELIVERED, createOrder(Order.Status.COMPLETED).delivered().status)
    }

    @Test
    fun `delivered throws outside COMPLETED`() {
        (nonTerminal + Order.Status.DELIVERED + Order.Status.CANCELED).forEach { state ->
            val ex = assertThrows<IllegalOrderStateException> { createOrder(state).delivered() }
            assertEquals("Order must be COMPLETED. Current status: $state", ex.message)
        }
    }

    @Test
    fun `transitions preserve immutability`() {
        val original = createOrder(Order.Status.EXECUTION_ENQUEUED)
        val next = original.inProgress()
        assertEquals(Order.Status.EXECUTION_ENQUEUED, original.status)
        assertEquals(Order.Status.IN_PROGRESS, next.status)
    }

    @Test
    fun `canScheduleVehicleDelivery only when RECEIVED`() {
        assertTrue(createOrder(Order.Status.RECEIVED).canScheduleVehicleDelivery())
        (nonTerminal - Order.Status.RECEIVED + terminal)
            .forEach { assertFalse(createOrder(it).canScheduleVehicleDelivery()) }
    }

    @Test
    fun `canScheduleVehicleReturn only when COMPLETED`() {
        assertTrue(createOrder(Order.Status.COMPLETED).canScheduleVehicleReturn())
        (nonTerminal + Order.Status.DELIVERED + Order.Status.CANCELED)
            .forEach { assertFalse(createOrder(it).canScheduleVehicleReturn()) }
    }

    @Test
    fun `new order defaults to RECEIVED with no items, no diagnosis and keeps who opened it`() {
        val order = createOrder()
        assertEquals(Order.Status.RECEIVED, order.status)
        assertTrue(order.services.isEmpty())
        assertTrue(order.supplies.isEmpty())
        assertNull(order.diagnosedBy)
        assertEquals(openedBy, order.openedBy)
    }
}
