package br.com.soat.shared

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
