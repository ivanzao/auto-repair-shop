package br.com.soat.order.event

import br.com.soat.customer.model.Customer
import br.com.soat.order.model.Order
import br.com.soat.service.model.Service
import br.com.soat.supply.model.SupplyRequirement
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderCreatedPayloadTest {

    private val mapper = ObjectMapper().registerKotlinModule()

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
    fun `maps order into a lean payload with priced services and part references`() {
        val partId = UUID.randomUUID()
        val service = Service(name = "Oil Change", description = null, price = BigDecimal("149.90"))
        val o = order(services = listOf(service), extraSupplies = listOf(SupplyRequirement(partId, 2)))

        val payload = OrderCreatedPayload.from(o)

        assertEquals(o.id, payload.orderId)
        assertEquals(customer.id, payload.customer.id)
        assertEquals("john@example.com", payload.customer.email)
        assertEquals("ABC1234", payload.vehicle.plate)
        assertEquals("Corolla", payload.vehicle.model)
        assertEquals(listOf(BigDecimal("149.90")), payload.services.map { it.price })
        assertEquals(listOf(partId), payload.parts.map { it.id })
        assertEquals(listOf(2), payload.parts.map { it.quantity })
    }

    @Test
    fun `serialized payload carries no part prices, names or total`() {
        val service = Service(name = "Oil Change", description = null, price = BigDecimal("149.90"))
        val o = order(services = listOf(service), extraSupplies = listOf(SupplyRequirement(UUID.randomUUID(), 2)))

        val json = mapper.readTree(mapper.writeValueAsString(OrderCreatedPayload.from(o)))

        assertTrue(json["totalAmount"] == null, "OrderCreated must not carry a total")
        assertTrue(json["services"].all { it["price"] != null }, "services are priced (order owns the catalog)")
        json["parts"].forEach { part ->
            assertFalse(part.has("unitPrice"), "parts must not carry unitPrice")
            assertFalse(part.has("name"), "parts must not carry name")
            assertTrue(part.has("id") && part.has("quantity"))
        }
    }
}
