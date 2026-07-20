package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderLoggingTest {

    private val customer = Customer(
        name = "John Doe",
        document = Document("12345678909"),
        email = Email("john@example.com"),
        contact = PhoneNumber("11999999999"),
    )
    private val vehicle = Vehicle(
        clientId = customer.id,
        plate = VehiclePlate("ABC1234"),
        brand = "Toyota",
        model = "Corolla",
        year = 2024,
    )
    private val attendantId = UUID.randomUUID()

    private val order = Order(
        customer = customer,
        vehicle = vehicle,
        attendantId = attendantId,
        description = "engine noise",
    )

    @Test
    fun `carries the order correlation fields`() {
        val rendered = order.logParams().map { it.toString() }

        assertEquals(
            listOf(
                kv("orderId", order.id).toString(),
                kv("customerId", customer.id).toString(),
                kv("vehicleId", vehicle.id).toString(),
                kv("attendantId", attendantId).toString(),
                kv("status", "RECEIVED").toString(),
            ),
            rendered,
        )
    }

    @Test
    fun `appends extra arguments after the standard ones`() {
        val params = order.logParams(kv("event", "order.created"))

        assertEquals(6, params.size)
        assertEquals(kv("event", "order.created").toString(), params.last().toString())
    }
}
