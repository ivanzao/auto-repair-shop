package br.com.soat.vehicle

import br.com.soat.vehicle.model.Vehicle
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class VehiclePostgresRepository : VehicleRepository {

    override fun findById(id: UUID) = transaction {
        Vehicles.selectAll()
            .where { Vehicles.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toVehicle()
    }

    override fun create(vehicle: Vehicle) = transaction {
        Vehicles.insert {
            it[id] = vehicle.id
            it[createdAt] = vehicle.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = vehicle.modifiedAt.toKotlinLocalDateTime()
            it[version] = vehicle.version
            it[client] = vehicle.clientId
            it[plate] = vehicle.plate
            it[brand] = vehicle.brand
            it[model] = vehicle.model
            it[year] = vehicle.year
        }.resultedValues?.singleOrNull()
            ?.toVehicle()
            ?: throw IllegalStateException("An error occurred while saving User")
    }
}