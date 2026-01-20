package br.com.soat.supply.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class SupplyNotFoundException(val supplyId: UUID) : ApplicationException("SUP-001", "Supply not found with id: $supplyId")
class InsufficientStockException(val supplyId: UUID) : ApplicationException("SUP-002", "Insufficient stock for supply: $supplyId")