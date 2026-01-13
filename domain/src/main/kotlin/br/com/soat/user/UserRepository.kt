package br.com.soat.user

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.user.model.User
import java.util.UUID

interface UserRepository {
    fun findById(id: UUID): User?
    fun findByEmail(email: Email): User?
    fun findByDocument(document: Document): User?
    fun findAll(): List<User>
    fun create(user: User): User
    fun update(user: User): User
    fun delete(id: UUID)
}