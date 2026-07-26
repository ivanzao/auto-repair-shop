package br.com.soat.order.event

import br.com.soat.customer.model.Customer
import br.com.soat.order.model.Order
import br.com.soat.shared.model.User
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class OrderCreatedEventTest {

    private val orderId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val customerId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private val customer = Customer(
        id = customerId,
        name = "Maria Silva",
        document = Document("12345678909"),
        email = Email("maria@exemplo.com"),
        contact = PhoneNumber("11999999999"),
    )

    private val vehicle = Vehicle(
        clientId = customerId,
        plate = VehiclePlate("ABC1234"),
        brand = "Volkswagen",
        model = "Gol 1.6",
        year = 2024,
    )

    private val order = Order(
        id = orderId,
        customer = customer,
        vehicle = vehicle,
        openedBy = User(UUID.randomUUID(), "12345678909"),
        description = "engine noise",
    )

    @Test
    fun `maps the order into the lean contract payload`() {
        val event = OrderCreatedEvent.from(order)

        assertEquals(orderId, event.orderId)
        assertEquals(customerId, event.customer.id)
        assertEquals("Maria Silva", event.customer.name)
        assertEquals("maria@exemplo.com", event.customer.email)
        assertEquals("ABC1234", event.vehicle.plate)
        assertEquals("Gol 1.6", event.vehicle.model)
    }

    @Test
    fun `carries the envelope identity inherited from DomainEvent`() {
        val event = OrderCreatedEvent.from(order)

        assertEquals("OrderCreated", event.eventType)
        assertEquals(1, event.eventVersion)
        assertNotNull(event.eventId)
        assertNotNull(event.occurredAt)
    }
}
