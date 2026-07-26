package br.com.soat.order.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class OrderNotFoundException(id: UUID) : ApplicationException("ORD-001", "Order not found with id: $id")
class IllegalOrderStateException(message: String) : ApplicationException("ORD-002", message)
class IllegalOrderCommandException(message: String) : ApplicationException("ORD-003", message)