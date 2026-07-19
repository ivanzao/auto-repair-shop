package br.com.soat.order

import br.com.soat.customer.model.Customer
import br.com.soat.metric.MetricsPort
import br.com.soat.order.model.Order
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.model.Page
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderStatusUseCaseTest {

    private class FakeOrderRepository(seed: Order) : OrderRepository {
        val store = mutableMapOf(seed.id to seed)
        override fun findById(id: UUID): Order? = store[id]
        override fun findAllPaginated(page: Int): Page<Order> = throw UnsupportedOperationException()
        override fun create(order: Order): Order { store[order.id] = order; return order }
        override fun update(order: Order): Order { store[order.id] = order; return order }
    }

    private object NoopMetrics : MetricsPort {
        private val counter = object : MetricsPort.Counter { override fun increment(amount: Double) {} }
        private val timer = object : MetricsPort.Timer {
            override fun record(durationMs: Long) {}
            override fun <T> recordSupplier(block: () -> T): T = block()
        }
        override fun counter(name: String, description: String, tags: Map<String, String>) = counter
        override fun timer(name: String, description: String, tags: Map<String, String>) = timer
    }

    private fun order(status: Order.Status): Order = Order(
        customer = Customer(
            name = "John", document = Document("12345678909"),
            email = Email("john@example.com"), contact = PhoneNumber("11999999999"),
        ),
        vehicle = Vehicle(
            clientId = UUID.randomUUID(), plate = VehiclePlate("ABC1234"),
            brand = "Toyota", model = "Corolla", year = 2024,
        ),
        attendantId = UUID.randomUUID(),
        description = "d",
        status = status,
    )

    @Test
    fun `payment confirmed moves RECEIVED to IN_PROGRESS`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        OrderStatusUseCase(repo, NoopMetrics).onPaymentConfirmed(o.id, BigDecimal("209.90"))
        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
    }

    @Test
    fun `execution finished completes an IN_PROGRESS order`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        OrderStatusUseCase(repo, NoopMetrics).onExecutionFinished(o.id)
        assertEquals(Order.Status.COMPLETED, repo.store[o.id]!!.status)
    }

    @Test
    fun `compensation cancels an IN_PROGRESS order`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        OrderStatusUseCase(repo, NoopMetrics).onCanceled(o.id, "ExecutionFailed")
        assertEquals(Order.Status.CANCELED, repo.store[o.id]!!.status)
    }

    @Test
    fun `compensation does not cancel a COMPLETED order`() {
        val o = order(Order.Status.COMPLETED)
        val repo = FakeOrderRepository(o)
        OrderStatusUseCase(repo, NoopMetrics).onCanceled(o.id, "ExecutionFailed")
        assertEquals(Order.Status.COMPLETED, repo.store[o.id]!!.status)
    }

    @Test
    fun `payment confirmed is idempotent when already IN_PROGRESS`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        OrderStatusUseCase(repo, NoopMetrics).onPaymentConfirmed(o.id, null)
        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
    }

    @Test
    fun `execution progress does not change status`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        OrderStatusUseCase(repo, NoopMetrics).onExecutionProgress(o.id, "DiagnoseFinished")
        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
    }

    @Test
    fun `unknown order is ignored`() {
        val repo = FakeOrderRepository(order(Order.Status.RECEIVED))
        // does not throw
        OrderStatusUseCase(repo, NoopMetrics).onExecutionFinished(UUID.randomUUID())
    }
}
