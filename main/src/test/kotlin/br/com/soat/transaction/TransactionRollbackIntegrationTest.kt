package br.com.soat.transaction

import br.com.soat.IntegrationTest
import br.com.soat.service.model.Service
import br.com.soat.service.repository.ServiceRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TransactionRollbackIntegrationTest : IntegrationTest() {

    private fun newService(name: String, price: BigDecimal): Service {
        val repository = get<ServiceRepository>()
        return repository.create(
            Service(name = name, description = "tx test", price = price)
        )
    }

    @Test
    fun `should rollback all changes when transaction fails`() {
        val repository = get<ServiceRepository>()
        val tx = get<RepositoryTransactionHandler>()

        val service = newService("Service for Rollback", BigDecimal("100.00"))
        assertEquals(BigDecimal("100.00"), repository.findById(service.id)!!.price)

        assertThrows(RuntimeException::class.java) {
            tx.inTransaction {
                repository.update(service.copy(price = BigDecimal("50.00")))
                throw RuntimeException("Simulated failure during transaction")
            }
        }

        assertEquals(
            BigDecimal("100.00"), repository.findById(service.id)!!.price,
            "Price should be rolled back after transaction failure"
        )
    }

    @Test
    fun `should commit all changes when transaction succeeds`() {
        val repository = get<ServiceRepository>()
        val tx = get<RepositoryTransactionHandler>()

        val service = newService("Service for Commit", BigDecimal("100.00"))

        tx.inTransaction {
            repository.update(repository.findById(service.id)!!.copy(price = BigDecimal("50.00")))
        }

        assertEquals(
            BigDecimal("50.00"), repository.findById(service.id)!!.price,
            "Price should be committed after successful transaction"
        )
    }

    @Test
    fun `should rollback multiple repository operations atomically`() {
        val repository = get<ServiceRepository>()
        val tx = get<RepositoryTransactionHandler>()

        val service1 = newService("Service 1", BigDecimal("100.00"))
        val service2 = newService("Service 2", BigDecimal("200.00"))

        assertThrows(RuntimeException::class.java) {
            tx.inTransaction {
                repository.update(repository.findById(service1.id)!!.copy(price = BigDecimal("90.00")))
                repository.update(repository.findById(service2.id)!!.copy(price = BigDecimal("180.00")))
                throw RuntimeException("Simulated failure after multiple updates")
            }
        }

        assertEquals(BigDecimal("100.00"), repository.findById(service1.id)!!.price, "Service 1 should be rolled back")
        assertEquals(BigDecimal("200.00"), repository.findById(service2.id)!!.price, "Service 2 should be rolled back")
    }
}
