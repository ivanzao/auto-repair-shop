package br.com.soat.order.event

import br.com.soat.customer.model.Customer
import br.com.soat.order.model.Order
import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.shared.model.User
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class OrderAwaitingApprovalEventTest {

    private val orderId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val customerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val reservationId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val serviceId = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val supplyId = UUID.fromString("66666666-6666-6666-6666-666666666666")

    private val customer = Customer(
        id = customerId,
        name = "Maria Silva",
        document = Document("12345678909"),
        email = Email("maria@exemplo.com"),
        contact = PhoneNumber("11999999999"),
    )

    private val order = Order(
        id = orderId,
        status = Order.Status.WAITING_APPROVAL,
        customer = customer,
        vehicle = Vehicle(
            clientId = customerId,
            plate = VehiclePlate("ABC1234"),
            brand = "Volkswagen",
            model = "Gol 1.6",
            year = 2024,
        ),
        openedBy = User(UUID.randomUUID(), "12345678909"),
        description = "engine noise",
        services = listOf(QuotedService(serviceId, "Troca de oleo", BigDecimal("100.00"))),
        supplies = listOf(QuotedSupply(supplyId, "Filtro de oleo", 2, BigDecimal("30.00"))),
    )

    @Test
    fun `maps the order into the frozen contract payload`() {
        val event = OrderAwaitingApprovalEvent.from(order, reservationId, BigDecimal("160.00"))

        assertEquals(orderId, event.orderId)
        assertEquals(reservationId, event.reservationId)
        assertEquals("Maria Silva", event.customer.name)
        assertEquals("maria@exemplo.com", event.customer.email)
        assertEquals(
            listOf(OrderAwaitingApprovalEvent.ServiceLine(serviceId, "Troca de oleo", BigDecimal("100.00"))),
            event.services,
        )
        assertEquals(
            listOf(OrderAwaitingApprovalEvent.SupplyLine(supplyId, "Filtro de oleo", 2, BigDecimal("30.00"))),
            event.supplies,
        )
        assertEquals(BigDecimal("160.00"), event.totalAmount)
    }

    @Test
    fun `takes the customer from the order, never from the reservation`() {
        val event = OrderAwaitingApprovalEvent.from(order, reservationId, BigDecimal("160.00"))

        assertEquals(order.customer.name, event.customer.name)
        assertEquals(order.customer.email.value, event.customer.email)
    }

    @Test
    fun `carries the envelope identity inherited from DomainEvent`() {
        val event = OrderAwaitingApprovalEvent.from(order, reservationId, BigDecimal("160.00"))

        assertEquals("OrderAwaitingApproval", event.eventType)
        assertEquals(1, event.eventVersion)
        assertNotNull(event.eventId)
        assertNotNull(event.occurredAt)
    }
}
