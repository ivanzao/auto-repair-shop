package br.com.soat.shared

import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

fun Table.email(name: String): Column<Email> =
    varchar(name, 255).transform(
        wrap = { Email(it) },
        unwrap = { it.value }
    )

fun Table.document(name: String): Column<Document> =
    varchar(name, 255).transform(
        wrap = { Document(it) },
        unwrap = { it.value }
    )

fun Table.phoneNumber(name: String): Column<PhoneNumber> =
    varchar(name, 255).transform(
        wrap = { PhoneNumber(it) },
        unwrap = { it.value }
    )

fun Table.vehiclePlate(name: String): Column<VehiclePlate> =
    varchar(name, 255).transform(
        wrap = { VehiclePlate(it) },
        unwrap = { it.value }
    )
