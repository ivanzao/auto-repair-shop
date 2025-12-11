package br.com.soat.user

import br.com.soat.user.model.User
import java.util.UUID

interface UserRepository {
    fun findById(id: UUID): User?
    fun findByEmail(email: String): User?
    fun findByDocument(document: String): User?
    fun findAll(): List<User>
    fun create(user: User): User
    fun update(user: User): User
    fun delete(id: UUID)
}