package br.com.soat.attendant

import br.com.soat.attendant.model.Attendant
import br.com.soat.exception.OptimisticLockException
import br.com.soat.shared.vo.Document
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class AttendantPostgresRepository : AttendantRepository {

    override fun findById(id: UUID) = transaction {
        Attendants.selectAll()
            .where { Attendants.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toAttendant()
    }

    override fun findByDocument(document: Document) = transaction {
        Attendants.selectAll()
            .where { Attendants.document eq document }
            .limit(1)
            .firstOrNull()
            ?.toAttendant()
    }

    override fun findAll(): List<Attendant> = transaction {
        Attendants.selectAll().map { it.toAttendant() }
    }

    override fun create(attendant: Attendant) = transaction {
        Attendants.insert {
            it[id] = attendant.id
            it[createdAt] = attendant.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = attendant.modifiedAt.toKotlinLocalDateTime()
            it[version] = attendant.version
            it[name] = attendant.name
            it[Attendants.document] = attendant.document
            it[Attendants.email] = attendant.email
            it[Attendants.contact] = attendant.contact
        }.resultedValues?.singleOrNull()?.toAttendant()
            ?: throw IllegalStateException("An error occurred while saving Attendant")
    }

    override fun update(attendant: Attendant): Attendant = transaction {
        val rows = Attendants
            .update({ (Attendants.id eq attendant.id) and (Attendants.version eq attendant.version) }) {
                it[modifiedAt] = attendant.modifiedAt.toKotlinLocalDateTime()
                it[version] = attendant.version + 1
                it[name] = attendant.name
                it[document] = attendant.document
                it[email] = attendant.email
                it[contact] = attendant.contact
            }

        if (rows == 0) {
            throw OptimisticLockException(
                "Attendant ${attendant.id} was modified by another transaction (expected version ${attendant.version})"
            )
        }

        Attendants
            .selectAll()
            .where { Attendants.id eq attendant.id }
            .limit(1)
            .first()
            .toAttendant()
    }

    override fun delete(id: UUID) {
        transaction {
            Attendants.deleteWhere { Attendants.id eq id }
        }
    }
}
