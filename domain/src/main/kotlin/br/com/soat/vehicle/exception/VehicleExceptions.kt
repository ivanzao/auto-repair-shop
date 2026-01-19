package br.com.soat.vehicle.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class VehicleNotFoundException(id: UUID) : ApplicationException("VEH-001", "Vehicle not found with id: $id")