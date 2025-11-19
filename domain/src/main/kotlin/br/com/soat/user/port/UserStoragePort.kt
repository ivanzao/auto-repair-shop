package br.com.soat.br.com.soat.user.port

import br.com.soat.br.com.soat.user.model.User
import java.util.UUID

interface UserStoragePort {
    fun findById(id: UUID): User?
    fun findByDocument(document: String): User?
    fun findAll(): List<User>
    fun save(user: User): User
    fun update(user: User): User
    fun delete(id: UUID)
}