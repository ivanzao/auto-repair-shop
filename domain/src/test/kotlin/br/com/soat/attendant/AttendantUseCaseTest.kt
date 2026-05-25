package br.com.soat.attendant

import br.com.soat.attendant.exception.AttendantAlreadyExistsException
import br.com.soat.attendant.exception.AttendantNotFoundException
import br.com.soat.attendant.model.Attendant
import br.com.soat.attendant.model.CreateAttendantRequest
import br.com.soat.attendant.model.UpdateAttendantRequest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.UUID.randomUUID

class AttendantUseCaseTest {

    private lateinit var repo: InMemoryAttendantRepository
    private lateinit var useCase: AttendantUseCase

    @BeforeEach
    fun setup() {
        repo = InMemoryAttendantRepository()
        useCase = AttendantUseCase(repo)
    }

    @Test
    fun `create persists attendant when document is unique`() {
        val req = CreateAttendantRequest(
            name = "Alice",
            document = Document("12345678901"),
            email = Email("alice@example.com"),
            contact = PhoneNumber("11999990000"),
        )

        val created = useCase.create(req)

        assertEquals("Alice", created.name)
        assertEquals(req.document, created.document)
        assertEquals(1, repo.findAll().size)
    }

    @Test
    fun `create throws when document already exists`() {
        val req = CreateAttendantRequest(
            name = "Alice",
            document = Document("12345678901"),
            email = Email("alice@example.com"),
            contact = PhoneNumber("11999990000"),
        )
        useCase.create(req)

        assertThrows(AttendantAlreadyExistsException::class.java) { useCase.create(req) }
    }

    @Test
    fun `findById throws when not found`() {
        assertThrows(AttendantNotFoundException::class.java) {
            useCase.findById(randomUUID())
        }
    }

    @Test
    fun `update mutates name email document contact`() {
        val created = useCase.create(
            CreateAttendantRequest(
                "Alice", Document("12345678901"), Email("a@x.com"), PhoneNumber("11999990000")
            )
        )

        val updated = useCase.update(
            created.id,
            UpdateAttendantRequest("Bob", Document("22233344455"), Email("b@x.com"), PhoneNumber("11988880000"))
        )

        assertEquals("Bob", updated.name)
        assertEquals(Document("22233344455"), updated.document)
    }

    @Test
    fun `delete removes attendant`() {
        val created = useCase.create(
            CreateAttendantRequest(
                "Alice", Document("12345678901"), Email("a@x.com"), PhoneNumber("11999990000")
            )
        )
        useCase.delete(created.id)
        assertEquals(0, repo.findAll().size)
    }
}

private class InMemoryAttendantRepository : AttendantRepository {
    private val store = mutableMapOf<UUID, Attendant>()

    override fun create(attendant: Attendant): Attendant {
        store[attendant.id] = attendant
        return attendant
    }

    override fun update(attendant: Attendant): Attendant {
        store[attendant.id] = attendant
        return attendant
    }

    override fun delete(id: UUID) { store.remove(id) }
    override fun findById(id: UUID): Attendant? = store[id]
    override fun findByDocument(document: Document): Attendant? = store.values.firstOrNull { it.document == document }
    override fun findAll(): List<Attendant> = store.values.toList()
}
