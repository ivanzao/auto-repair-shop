package br.com.soat.vehicle

import br.com.soat.vehicle.model.Vehicle
import java.util.UUID

interface VehicleRepository {
    fun findById(id: UUID): Vehicle?
    fun create(vehicle: Vehicle): Vehicle
    fun findAll(): List<Vehicle>
    fun update(vehicle: Vehicle): Vehicle
    fun delete(id: UUID)
}