package br.com.soat.attendant

import br.com.soat.attendant.exception.AttendantAlreadyExistsException
import br.com.soat.attendant.exception.AttendantNotFoundException
import br.com.soat.attendant.model.Attendant
import br.com.soat.attendant.model.CreateAttendantRequest
import br.com.soat.attendant.model.UpdateAttendantRequest
import br.com.soat.attendant.repository.AttendantRepository
import java.util.UUID

class AttendantUseCase(
    private val repository: AttendantRepository,
) {

    fun findById(id: UUID): Attendant =
        repository.findById(id) ?: throw AttendantNotFoundException(id)

    fun findAll(): List<Attendant> = repository.findAll()

    fun create(request: CreateAttendantRequest): Attendant {
        repository.findByDocument(request.document)?.let { throw AttendantAlreadyExistsException() }

        return repository.create(
            Attendant(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact,
            )
        )
    }

    fun update(id: UUID, request: UpdateAttendantRequest): Attendant {
        val existing = repository.findById(id) ?: throw AttendantNotFoundException(id)
        return repository.update(
            existing.copy(
                name = request.name,
                document = request.document,
                email = request.email,
                contact = request.contact,
            )
        )
    }

    fun delete(id: UUID) {
        repository.delete(id)
    }
}
