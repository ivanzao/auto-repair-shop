package br.com.soat.user.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class UserNotFoundException(id: UUID) : ApplicationException("USR-001", "User not found with id: $id")
class UserAlreadyExistsException : ApplicationException("USR-002", "User already exists")
