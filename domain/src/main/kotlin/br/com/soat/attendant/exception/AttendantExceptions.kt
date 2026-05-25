package br.com.soat.attendant.exception

import java.util.UUID

class AttendantNotFoundException(id: UUID) : RuntimeException("Attendant not found: $id")
class AttendantAlreadyExistsException : RuntimeException("Attendant with this document already exists")
