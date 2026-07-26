package br.com.soat.transaction

import br.com.soat.IntegrationTest
import br.com.soat.customer.model.Customer
import br.com.soat.customer.repository.CustomerRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TransactionRollbackIntegrationTest : IntegrationTest() {

    private fun newCustomer(name: String, document: String, email: String): Customer {
        val repository = get<CustomerRepository>()
        return repository.create(
            Customer(
                name = name,
                document = Document(document),
                email = Email(email),
                contact = PhoneNumber("11999990000"),
            )
        )
    }

    @Test
    fun `should rollback all changes when transaction fails`() {
        val repository = get<CustomerRepository>()
        val tx = get<RepositoryTransactionHandler>()

        val customer = newCustomer("Rollback", "12345678901", "rollback@test.com")
        assertEquals("Rollback", repository.findById(customer.id)!!.name)

        assertThrows(RuntimeException::class.java) {
            tx.inTransaction {
                repository.update(customer.copy(name = "Changed"))
                throw RuntimeException("Simulated failure during transaction")
            }
        }

        assertEquals(
            "Rollback", repository.findById(customer.id)!!.name,
            "Name should be rolled back after transaction failure"
        )
    }

    @Test
    fun `should commit all changes when transaction succeeds`() {
        val repository = get<CustomerRepository>()
        val tx = get<RepositoryTransactionHandler>()

        val customer = newCustomer("Commit", "12345678902", "commit@test.com")

        tx.inTransaction {
            repository.update(repository.findById(customer.id)!!.copy(name = "Committed"))
        }

        assertEquals(
            "Committed", repository.findById(customer.id)!!.name,
            "Name should be committed after successful transaction"
        )
    }

    @Test
    fun `should rollback multiple repository operations atomically`() {
        val repository = get<CustomerRepository>()
        val tx = get<RepositoryTransactionHandler>()

        val first = newCustomer("First", "12345678903", "first@test.com")
        val second = newCustomer("Second", "12345678904", "second@test.com")

        assertThrows(RuntimeException::class.java) {
            tx.inTransaction {
                repository.update(repository.findById(first.id)!!.copy(name = "First Changed"))
                repository.update(repository.findById(second.id)!!.copy(name = "Second Changed"))
                throw RuntimeException("Simulated failure after multiple updates")
            }
        }

        assertEquals("First", repository.findById(first.id)!!.name, "First should be rolled back")
        assertEquals("Second", repository.findById(second.id)!!.name, "Second should be rolled back")
    }
}
