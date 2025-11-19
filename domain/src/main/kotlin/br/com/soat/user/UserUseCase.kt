package br.com.soat.br.com.soat.user

import br.com.soat.br.com.soat.user.model.CreateUserRequest
import br.com.soat.br.com.soat.user.model.User
import br.com.soat.br.com.soat.user.port.UserStoragePort

class UserUseCase(
    private val storagePort: UserStoragePort
) {

    fun find(id: String) = storagePort.findByDocument(id)

    fun create(request: CreateUserRequest): User {
        storagePort.findByDocument(request.document)?.let {
            throw IllegalArgumentException("User already exists")
        }

        return storagePort.save(
            User(
                name = request.name,
                email = request.email,
                document = request.document,
                contact = request.contact,
                role = request.role
            )
        )
    }
}