package br.com.soat.transaction

import br.com.soat.IntegrationTest
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.repository.SupplyRepository
import br.com.soat.supply.createSupply
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TransactionRollbackIntegrationTest : IntegrationTest() {

    @Test
    fun `should rollback all changes when transaction fails`() {
        val supplyRepository = get<SupplyRepository>()
        val tx = get<RepositoryTransactionHandler>()

        // Create initial supply with 100 units
        val initialSupply = createSupply(
            name = "Test Supply for Rollback",
            description = "Testing rollback",
            quantityInStock = 100,
            price = BigDecimal("10.00")
        )

        val supplyId = initialSupply.id

        // Verify initial stock
        val supplyBeforeTransaction = supplyRepository.findById(supplyId)!!
        assertEquals(100, supplyBeforeTransaction.quantityInStock, "Initial stock should be 100")

        // Attempt transaction that will fail
        assertThrows(RuntimeException::class.java) {
            tx.inTransaction {
                // Update supply stock (reduce by 50)
                val updatedSupply = supplyBeforeTransaction.copy(quantityInStock = 50)
                supplyRepository.update(updatedSupply)

                // Force an exception to trigger rollback
                throw RuntimeException("Simulated failure during transaction")
            }
        }

        // Verify stock was rolled back to original value
        val supplyAfterRollback = supplyRepository.findById(supplyId)!!
        assertEquals(100, supplyAfterRollback.quantityInStock,
            "Stock should be rolled back to 100 after transaction failure")
    }

    @Test
    fun `should commit all changes when transaction succeeds`() {
        val supplyRepository = get<SupplyRepository>()
        val tx = get<RepositoryTransactionHandler>()

        // Create initial supply with 100 units
        val initialSupply = createSupply(
            name = "Test Supply for Commit",
            description = "Testing commit",
            quantityInStock = 100,
            price = BigDecimal("10.00")
        )

        val supplyId = initialSupply.id

        // Execute successful transaction
        tx.inTransaction {
            val supply = supplyRepository.findById(supplyId)!!
            val updatedSupply = supply.copy(quantityInStock = 50)
            supplyRepository.update(updatedSupply)
            // No exception - transaction should commit
        }

        // Verify stock was persisted
        val supplyAfterCommit = supplyRepository.findById(supplyId)!!
        assertEquals(50, supplyAfterCommit.quantityInStock,
            "Stock should be committed to 50 after successful transaction")
    }

    @Test
    fun `should rollback multiple repository operations atomically`() {
        val supplyRepository = get<SupplyRepository>()
        val tx = get<RepositoryTransactionHandler>()

        // Create initial data
        val supply1 = createSupply(
            name = "Supply 1",
            description = "First supply",
            quantityInStock = 100,
            price = BigDecimal("10.00")
        )

        val supply2 = createSupply(
            name = "Supply 2",
            description = "Second supply",
            quantityInStock = 200,
            price = BigDecimal("20.00")
        )

        // Get initial values
        val initialStock1 = supplyRepository.findById(supply1.id)!!.quantityInStock
        val initialStock2 = supplyRepository.findById(supply2.id)!!.quantityInStock

        // Attempt transaction that modifies multiple entities
        assertThrows(RuntimeException::class.java) {
            tx.inTransaction {
                // Update first supply
                val updated1 = supplyRepository.findById(supply1.id)!!
                    .copy(quantityInStock = initialStock1 - 10)
                supplyRepository.update(updated1)

                // Update second supply
                val updated2 = supplyRepository.findById(supply2.id)!!
                    .copy(quantityInStock = initialStock2 - 20)
                supplyRepository.update(updated2)

                // Force failure after both updates
                throw RuntimeException("Simulated failure after multiple updates")
            }
        }

        // Verify ALL changes were rolled back
        assertEquals(initialStock1, supplyRepository.findById(supply1.id)!!.quantityInStock,
            "Supply 1 stock should be rolled back")
        assertEquals(initialStock2, supplyRepository.findById(supply2.id)!!.quantityInStock,
            "Supply 2 stock should be rolled back")
    }
}
