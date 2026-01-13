package br.com.soat.vehicle

import br.com.soat.shared.vehiclePlate
import br.com.soat.vehicle.model.Vehicle
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Vehicles : Table() {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val client = uuid("client_id")

    val plate = vehiclePlate("plate")
    val brand = varchar("brand", 255)
    val model = varchar("model", 255)
    val year = integer("year")

    init {
        PrimaryKey(id)
        uniqueIndex(plate)
    }
}

fun ResultRow.toVehicle(): Vehicle = Vehicle(
    id = this[Vehicles.id],
    createdAt = this[Vehicles.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[Vehicles.modifiedAt].toJavaLocalDateTime(),
    version = this[Vehicles.version],
    clientId = this[Vehicles.client],
    plate = this[Vehicles.plate],
    brand = this[Vehicles.brand],
    model = this[Vehicles.model],
    year = this[Vehicles.year]
)