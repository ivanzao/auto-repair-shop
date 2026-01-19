package br.com.soat.user

import br.com.soat.security.HashService
import br.com.soat.user.model.CreateUserRequest
import br.com.soat.user.model.UpdateUserRequest
import br.com.soat.user.model.User
import java.util.UUID

class UserUseCase(
    private val storagePort: UserRepository,
    private val hashService: HashService,
) {

    fun findById(id: UUID): User? = storagePort.findById(id)

    fun findAll(): List<User> = storagePort.findAll()

    fun create(request: CreateUserRequest): User {
        storagePort.findByDocument(request.document)?.let {
            throw IllegalArgumentException("User already exists")
        }

        return storagePort.create(
            User(
                name = request.name,
                email = request.email,
                document = request.document,
                contact = request.contact,
                role = request.role,
                hashedPassword = hashService.hash(request.password)
            )
        )
    }

    fun update(id: UUID, request: UpdateUserRequest): User? {
        val existingUser = storagePort.findById(id) ?: return null

        val updatedUser = existingUser.copy(
            name = request.name,
            email = request.email,
            document = request.document,
            contact = request.contact,
            role = request.role
        )

        return storagePort.update(updatedUser)
    }

    fun delete(id: UUID): Boolean {
        val existingUser = storagePort.findById(id) ?: return false
        storagePort.delete(existingUser.id)
        return true
    }
}