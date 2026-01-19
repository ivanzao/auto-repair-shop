package br.com.soat.customer.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class CustomerNotFoundException(id: UUID) : ApplicationException("CUS-001", "Customer not found with id: $id")