package br.com.soat.vehicle

import br.com.soat.vehicle.model.Vehicle
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class VehiclePostgresRepository : VehicleRepository {

    override fun findById(id: UUID) = transaction {
        Vehicles.selectAll()
            .where { Vehicles.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toVehicle()
    }

    override fun findAll(): List<Vehicle> = transaction {
        Vehicles.selectAll().map { it.toVehicle() }
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
            ?: throw IllegalStateException("An error occurred while saving Vehicle")
    }

    override fun update(vehicle: Vehicle): Vehicle = transaction {
        Vehicles.update({ Vehicles.id eq vehicle.id }) {
            it[modifiedAt] = vehicle.modifiedAt.toKotlinLocalDateTime()
            it[version] = vehicle.version + 1
            it[client] = vehicle.clientId
            it[plate] = vehicle.plate
            it[brand] = vehicle.brand
            it[model] = vehicle.model
            it[year] = vehicle.year
        }

        findById(vehicle.id) ?: throw IllegalStateException("Vehicle not found after update")
    }

    override fun delete(id: UUID) {
        transaction {
            Vehicles.deleteWhere { Vehicles.id eq id }
        }
    }
}
