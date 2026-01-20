package br.com.soat.event

import br.com.soat.event.model.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Events : Table("events") {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val type = varchar("type", 255)
    val status = varchar("status", 50)
    val payload = text("payload")

    init {
        PrimaryKey(id)
    }
}

private val objectMapper = ObjectMapper().findAndRegisterModules()!!

fun ResultRow.toDomainEvent(): DomainEvent {
    val typeName = this[Events.type]
    val payload = this[Events.payload]
    val clazz = Class.forName(typeName).kotlin
    return objectMapper.readValue(payload, clazz.java) as DomainEvent
}