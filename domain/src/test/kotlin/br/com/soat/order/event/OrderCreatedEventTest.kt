package br.com.soat.order.event

import br.com.soat.customer.model.Customer
import br.com.soat.order.model.Order
import br.com.soat.service.model.Service
import br.com.soat.shared.model.SupplyRequirement
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

class OrderCreatedEventTest {

    private val customer = Customer(
        name = "John Doe",
        document = Document("12345678909"),
        email = Email("John@Example.com"),
        contact = PhoneNumber("11999999999"),
    )
    private val vehicle = Vehicle(
        clientId = customer.id,
        plate = VehiclePlate("ABC1234"),
        brand = "Toyota",
        model = "Corolla",
        year = 2024,
    )

    private fun order(services: List<Service> = emptyList(), extraSupplies: List<SupplyRequirement> = emptyList()) =
        Order(
            customer = customer,
            vehicle = vehicle,
            attendantId = UUID.randomUUID(),
            description = "engine noise",
            services = services,
            extraSupplies = extraSupplies,
        )

    @Test
    fun `maps order into a lean event with priced services and supply references`() {
        val supplyId = UUID.randomUUID()
        val service = Service(name = "Oil Change", description = null, price = BigDecimal("149.90"))
        val o = order(services = listOf(service), extraSupplies = listOf(SupplyRequirement(supplyId, 2)))

        val event = OrderCreatedEvent.from(o)

        assertEquals(o.id, event.orderId)
        assertEquals(customer.id, event.customer.id)
        assertEquals("john@example.com", event.customer.email)
        assertEquals("ABC1234", event.vehicle.plate)
        assertEquals("Corolla", event.vehicle.model)
        assertEquals(listOf(BigDecimal("149.90")), event.services.map { it.price })
        assertEquals(listOf(supplyId), event.supplies.map { it.id })
        assertEquals(listOf(2), event.supplies.map { it.quantity })
    }

    @Test
    fun `consolidates service and extra supplies by supplyId`() {
        val supplyId = UUID.randomUUID()
        val service = Service(
            name = "Oil Change", description = null, price = BigDecimal("149.90"),
            requiredSupplies = listOf(SupplyRequirement(supplyId, 4)),
        )
        val o = order(services = listOf(service), extraSupplies = listOf(SupplyRequirement(supplyId, 6)))

        val event = OrderCreatedEvent.from(o)

        assertEquals(1, event.supplies.size)
        assertEquals(10, event.supplies.single().quantity)
    }

    @Test
    fun `carries the envelope identity inherited from DomainEvent`() {
        val event = OrderCreatedEvent.from(order())

        assertEquals("OrderCreated", event.eventType)
        assertEquals(1, event.eventVersion)
        assertNotNull(event.eventId)
        assertNotNull(event.occurredAt)
    }
}
