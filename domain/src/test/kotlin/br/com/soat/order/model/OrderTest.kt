package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.order.exception.IllegalOrderStateException
import br.com.soat.service.model.Service
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.supply.model.SupplyRequirement
import br.com.soat.user.model.User
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

    private val attendant = User(
        name = "Jane Attendant",
        hashedPassword = "hashedPassword",
        document = Document("98765432100"),
        email = Email("jane@example.com"),
        contact = PhoneNumber("11988888888"),
        role = User.Role.ATTENDANT
    )

    private fun createOrder(status: Order.Status = Order.Status.RECEIVED) = Order(
        customer = customer,
        vehicle = vehicle,
        attendant = attendant,
        description = "Test order",
        status = status
    )

    @Test
    fun `should transition from RECEIVED to IN_DIAGNOSIS`() {
        val order = createOrder(Order.Status.RECEIVED)

        val result = order.inDiagnosis("Tech John")

        assertEquals(Order.Status.IN_DIAGNOSIS, result.status)
        assertEquals("Tech John", result.technician)
    }

    @Test
    fun `should throw when transitioning to IN_DIAGNOSIS from invalid state`() {
        val invalidStates = listOf(
            Order.Status.IN_DIAGNOSIS,
            Order.Status.WAITING_APPROVAL,
            Order.Status.IN_PROGRESS,
            Order.Status.COMPLETED,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        invalidStates.forEach { state ->
            val order = createOrder(state)

            val exception = assertThrows<IllegalOrderStateException> {
                order.inDiagnosis("Tech John")
            }

            assertEquals("Order must be RECEIVED. Current status: $state", exception.message)
        }
    }

    @Test
    fun `should transition from IN_DIAGNOSIS to WAITING_APPROVAL`() {
        val order = createOrder(Order.Status.IN_DIAGNOSIS)

        val result = order.waitingApproval()

        assertEquals(Order.Status.WAITING_APPROVAL, result.status)
    }

    @Test
    fun `should throw when transitioning to WAITING_APPROVAL from invalid state`() {
        val invalidStates = listOf(
            Order.Status.RECEIVED,
            Order.Status.WAITING_APPROVAL,
            Order.Status.IN_PROGRESS,
            Order.Status.COMPLETED,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        invalidStates.forEach { state ->
            val order = createOrder(state)

            val exception = assertThrows<IllegalOrderStateException> {
                order.waitingApproval()
            }

            assertEquals("Order must be IN_DIAGNOSIS. Current status: $state", exception.message)
        }
    }

    @Test
    fun `should transition from WAITING_APPROVAL to IN_PROGRESS`() {
        val order = createOrder(Order.Status.WAITING_APPROVAL)

        val result = order.inProgress()

        assertEquals(Order.Status.IN_PROGRESS, result.status)
    }

    @Test
    fun `should throw when transitioning to IN_PROGRESS from invalid state`() {
        val invalidStates = listOf(
            Order.Status.RECEIVED,
            Order.Status.IN_DIAGNOSIS,
            Order.Status.IN_PROGRESS,
            Order.Status.COMPLETED,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        invalidStates.forEach { state ->
            val order = createOrder(state)

            val exception = assertThrows<IllegalOrderStateException> {
                order.inProgress()
            }

            assertEquals("Order must be WAITING_APPROVAL. Current status: $state", exception.message)
        }
    }

    @Test
    fun `should transition from IN_PROGRESS to COMPLETED`() {
        val order = createOrder(Order.Status.IN_PROGRESS)

        val result = order.completed()

        assertEquals(Order.Status.COMPLETED, result.status)
    }

    @Test
    fun `should throw when transitioning to COMPLETED from invalid state`() {
        val invalidStates = listOf(
            Order.Status.RECEIVED,
            Order.Status.IN_DIAGNOSIS,
            Order.Status.WAITING_APPROVAL,
            Order.Status.COMPLETED,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        invalidStates.forEach { state ->
            val order = createOrder(state)

            val exception = assertThrows<IllegalOrderStateException> {
                order.completed()
            }

            assertEquals("Order must be IN_PROGRESS. Current status: $state", exception.message)
        }
    }

    @Test
    fun `should transition from COMPLETED to DELIVERED`() {
        val order = createOrder(Order.Status.COMPLETED)

        val result = order.delivered()

        assertEquals(Order.Status.DELIVERED, result.status)
    }

    @Test
    fun `should throw when transitioning to DELIVERED from invalid state`() {
        val invalidStates = listOf(
            Order.Status.RECEIVED,
            Order.Status.IN_DIAGNOSIS,
            Order.Status.WAITING_APPROVAL,
            Order.Status.IN_PROGRESS,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        invalidStates.forEach { state ->
            val order = createOrder(state)

            val exception = assertThrows<IllegalOrderStateException> {
                order.delivered()
            }

            assertEquals("Order must be COMPLETED. Current status: $state", exception.message)
        }
    }

    @Test
    fun `should cancel order from RECEIVED`() {
        val order = createOrder(Order.Status.RECEIVED)

        val result = order.canceled()

        assertEquals(Order.Status.CANCELED, result.status)
    }

    @Test
    fun `should cancel order from IN_DIAGNOSIS`() {
        val order = createOrder(Order.Status.IN_DIAGNOSIS)

        val result = order.canceled()

        assertEquals(Order.Status.CANCELED, result.status)
    }

    @Test
    fun `should cancel order from WAITING_APPROVAL`() {
        val order = createOrder(Order.Status.WAITING_APPROVAL)

        val result = order.canceled()

        assertEquals(Order.Status.CANCELED, result.status)
    }

    @Test
    fun `should cancel order from IN_PROGRESS`() {
        val order = createOrder(Order.Status.IN_PROGRESS)

        val result = order.canceled()

        assertEquals(Order.Status.CANCELED, result.status)
    }

    @Test
    fun `should cancel order from DELIVERED`() {
        val order = createOrder(Order.Status.DELIVERED)

        val result = order.canceled()

        assertEquals(Order.Status.CANCELED, result.status)
    }

    @Test
    fun `should throw when canceling COMPLETED order`() {
        val order = createOrder(Order.Status.COMPLETED)

        val exception = assertThrows<IllegalOrderStateException> {
            order.canceled()
        }

        assertEquals("Order must not be COMPLETED", exception.message)
    }

    @Test
    fun `should throw when canceling already CANCELED order`() {
        val order = createOrder(Order.Status.CANCELED)

        val exception = assertThrows<IllegalOrderStateException> {
            order.canceled()
        }

        assertEquals("Order already canceled", exception.message)
    }

    @Test
    fun `should preserve immutability through transitions`() {
        val original = createOrder(Order.Status.RECEIVED)

        val afterDiagnosis = original.inDiagnosis("Tech John")

        assertEquals(Order.Status.RECEIVED, original.status)
        assertNull(original.technician)
        assertEquals(Order.Status.IN_DIAGNOSIS, afterDiagnosis.status)
        assertEquals("Tech John", afterDiagnosis.technician)
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
        val newServices = listOf(
            Service(name = "New Service", description = null, price = BigDecimal("20.00"))
        )

        val result = order.addServices(newServices)

        assertEquals(2, result.services.size)
        assertEquals("Initial Service", result.services[0].name)
        assertEquals("New Service", result.services[1].name)
    }

    @Test
    fun `should preserve immutability when adding services`() {
        val order = createOrder()
        val services = listOf(
            Service(name = "Service", description = null, price = BigDecimal("10.00"))
        )

        val result = order.addServices(services)

        assertTrue(order.services.isEmpty())
        assertEquals(1, result.services.size)
    }

    @Test
    fun `should add supply requirements to order`() {
        val order = createOrder()
        val supplyId = UUID.randomUUID()
        val requirements = listOf(SupplyRequirement(supplyId, 5))

        val result = order.addSupplyRequirements(requirements)

        assertEquals(1, result.extraSupplies.size)
        assertEquals(supplyId, result.extraSupplies[0].supplyId)
        assertEquals(5, result.extraSupplies[0].quantity)
    }

    @Test
    fun `should append supply requirements to existing ones`() {
        val oldSupplyId = UUID.randomUUID()
        val newSupplyId = UUID.randomUUID()
        val order = createOrder().copy(extraSupplies = listOf(SupplyRequirement(oldSupplyId, 3)))
        val newRequirements = listOf(SupplyRequirement(newSupplyId, 7))

        val result = order.addSupplyRequirements(newRequirements)

        assertEquals(2, result.extraSupplies.size)
        val supplyMap = result.extraSupplies.associateBy { it.supplyId }
        assertEquals(3, supplyMap[oldSupplyId]?.quantity)
        assertEquals(7, supplyMap[newSupplyId]?.quantity)
    }

    @Test
    fun `should consolidate supply requirements with same supplyId`() {
        val supplyId = UUID.randomUUID()
        val order = createOrder().copy(extraSupplies = listOf(SupplyRequirement(supplyId, 3)))
        val newRequirements = listOf(SupplyRequirement(supplyId, 5))

        val result = order.addSupplyRequirements(newRequirements)

        assertEquals(1, result.extraSupplies.size)
        assertEquals(supplyId, result.extraSupplies[0].supplyId)
        assertEquals(8, result.extraSupplies[0].quantity)
    }

    @Test
    fun `canScheduleVehicleDelivery should return true when RECEIVED`() {
        val order = createOrder(Order.Status.RECEIVED)

        assertTrue(order.canScheduleVehicleDelivery())
    }

    @Test
    fun `canScheduleVehicleDelivery should return false for other states`() {
        val otherStates = listOf(
            Order.Status.IN_DIAGNOSIS,
            Order.Status.WAITING_APPROVAL,
            Order.Status.IN_PROGRESS,
            Order.Status.COMPLETED,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        otherStates.forEach { state ->
            val order = createOrder(state)
            assertFalse(order.canScheduleVehicleDelivery())
        }
    }

    @Test
    fun `canScheduleVehicleReturn should return true when COMPLETED`() {
        val order = createOrder(Order.Status.COMPLETED)

        assertTrue(order.canScheduleVehicleReturn())
    }

    @Test
    fun `canScheduleVehicleReturn should return false for other states`() {
        val otherStates = listOf(
            Order.Status.RECEIVED,
            Order.Status.IN_DIAGNOSIS,
            Order.Status.WAITING_APPROVAL,
            Order.Status.IN_PROGRESS,
            Order.Status.DELIVERED,
            Order.Status.CANCELED
        )

        otherStates.forEach { state ->
            val order = createOrder(state)
            assertFalse(order.canScheduleVehicleReturn())
        }
    }

    @Test
    fun `getSupplyRequirements should return empty list when no supplies`() {
        val order = createOrder()

        val result = order.getSupplyRequirements()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSupplyRequirements should return extra supplies when no services`() {
        val supplyId = UUID.randomUUID()
        val order = createOrder().copy(extraSupplies = listOf(SupplyRequirement(supplyId, 5)))

        val result = order.getSupplyRequirements()

        assertEquals(1, result.size)
        assertEquals(supplyId, result[0].supplyId)
        assertEquals(5, result[0].quantity)
    }

    @Test
    fun `getSupplyRequirements should return service supplies when no extra supplies`() {
        val supplyId = UUID.randomUUID()
        val service = Service(
            name = "Service",
            description = null,
            price = BigDecimal("10.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId, 3))
        )
        val order = createOrder().copy(services = listOf(service))

        val result = order.getSupplyRequirements()

        assertEquals(1, result.size)
        assertEquals(supplyId, result[0].supplyId)
        assertEquals(3, result[0].quantity)
    }

    @Test
    fun `getSupplyRequirements should consolidate supplies from multiple services`() {
        val supplyId = UUID.randomUUID()
        val service1 = Service(
            name = "Service 1",
            description = null,
            price = BigDecimal("10.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId, 2))
        )
        val service2 = Service(
            name = "Service 2",
            description = null,
            price = BigDecimal("20.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId, 3))
        )
        val order = createOrder().copy(services = listOf(service1, service2))

        val result = order.getSupplyRequirements()

        assertEquals(1, result.size)
        assertEquals(supplyId, result[0].supplyId)
        assertEquals(5, result[0].quantity)
    }

    @Test
    fun `getSupplyRequirements should consolidate service supplies with extra supplies`() {
        val supplyId = UUID.randomUUID()
        val service = Service(
            name = "Service",
            description = null,
            price = BigDecimal("10.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId, 4))
        )
        val order = createOrder().copy(
            services = listOf(service),
            extraSupplies = listOf(SupplyRequirement(supplyId, 6))
        )

        val result = order.getSupplyRequirements()

        assertEquals(1, result.size)
        assertEquals(supplyId, result[0].supplyId)
        assertEquals(10, result[0].quantity)
    }

    @Test
    fun `getSupplyRequirements should keep different supplies separate`() {
        val supplyId1 = UUID.randomUUID()
        val supplyId2 = UUID.randomUUID()
        val service = Service(
            name = "Service",
            description = null,
            price = BigDecimal("10.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId1, 2))
        )
        val order = createOrder().copy(
            services = listOf(service),
            extraSupplies = listOf(SupplyRequirement(supplyId2, 3))
        )

        val result = order.getSupplyRequirements()

        assertEquals(2, result.size)
        val supplyMap = result.associateBy { it.supplyId }
        assertEquals(2, supplyMap[supplyId1]?.quantity)
        assertEquals(3, supplyMap[supplyId2]?.quantity)
    }

    @Test
    fun `getSupplyRequirements should consolidate multiple supplies from services and extra supplies`() {
        val supplyId1 = UUID.randomUUID()
        val supplyId2 = UUID.randomUUID()
        val service1 = Service(
            name = "Service 1",
            description = null,
            price = BigDecimal("10.00"),
            requiredSupplies = listOf(
                SupplyRequirement(supplyId1, 1),
                SupplyRequirement(supplyId2, 2)
            )
        )
        val service2 = Service(
            name = "Service 2",
            description = null,
            price = BigDecimal("20.00"),
            requiredSupplies = listOf(SupplyRequirement(supplyId1, 3))
        )
        val order = createOrder().copy(
            services = listOf(service1, service2),
            extraSupplies = listOf(
                SupplyRequirement(supplyId1, 5),
                SupplyRequirement(supplyId2, 7)
            )
        )

        val result = order.getSupplyRequirements()

        assertEquals(2, result.size)
        val supplyMap = result.associateBy { it.supplyId }
        assertEquals(9, supplyMap[supplyId1]?.quantity) // 1 + 3 + 5
        assertEquals(9, supplyMap[supplyId2]?.quantity) // 2 + 7
    }
}
