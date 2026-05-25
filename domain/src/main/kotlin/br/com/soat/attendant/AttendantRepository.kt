package br.com.soat.attendant

import br.com.soat.attendant.model.Attendant
import br.com.soat.shared.vo.Document
import java.util.UUID

interface AttendantRepository {
    fun create(attendant: Attendant): Attendant
    fun update(attendant: Attendant): Attendant
    fun delete(id: UUID)
    fun findById(id: UUID): Attendant?
    fun findByDocument(document: Document): Attendant?
    fun findAll(): List<Attendant>
}
