package br.com.soat.command

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Commands : Table("commands") {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)
    val status = varchar("status", 50)
    val type = varchar("type", 255)
    val payload = text("payload")

    init {
        PrimaryKey(id)
    }
}