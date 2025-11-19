package br.com.soat.user

import br.com.soat.br.com.soat.user.model.User
import br.com.soat.br.com.soat.user.port.UserStoragePort
import br.com.soat.exception.OptimisticLockException
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class UserPostgresStorage : UserStoragePort {

    override fun findById(id: UUID) = transaction {
        Users.selectAll()
            .where { Users.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toDomainUser()
    }

    override fun findByDocument(document: String) = transaction {
        Users.selectAll()
            .where { Users.document eq document }
            .limit(1)
            .firstOrNull()
            ?.toDomainUser()
    }

    override fun findAll(): List<User> = transaction {
        Users.selectAll()
            .map { it.toDomainUser() }
    }

    override fun save(user: User) = transaction {
        val statement = Users.insert {
            it[id] = user.id
            it[createdAt] = user.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = user.modifiedAt.toKotlinLocalDateTime()
            it[version] = user.version
            it[name] = user.name
            it[email] = user.email
            it[document] = user.document
            it[contact] = user.contact
            it[role] = user.role.name
        }

        val row = statement.resultedValues?.singleOrNull()
            ?: throw IllegalStateException("An error occurred while saving User")

        row.toDomainUser()
    }

    override fun update(user: User): User = transaction {
        val rows = Users
            .update({ (Users.id eq user.id) and (Users.version eq user.version) }) {
                it[modifiedAt] = user.modifiedAt.toKotlinLocalDateTime()
                it[name] = user.name
                it[email] = user.email
                it[document] = user.document
                it[contact] = user.contact
                it[role] = user.role.name
                it[version] = user.version + 1
            }

        if (rows == 0) {
            throw OptimisticLockException(
                "User ${user.id} was modified by another transaction (expected version ${user.version})"
            )
        }

        Users
            .selectAll()
            .where { Users.id eq user.id }
            .limit(1)
            .first()
            .toDomainUser()
    }

    override fun delete(id: UUID) {
        transaction {
            Users.deleteWhere { Users.id eq id }
        }
    }
}