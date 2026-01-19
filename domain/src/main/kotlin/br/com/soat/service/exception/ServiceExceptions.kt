package br.com.soat.service.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class ServiceNotFoundException(id: UUID) : ApplicationException("SVC-001", "Service not found with id: $id")