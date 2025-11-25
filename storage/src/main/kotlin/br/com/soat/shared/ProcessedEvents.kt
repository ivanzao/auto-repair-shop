package br.com.soat.shared

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object ProcessedEvents : Table("processed_events") {
    val eventId = uuid("event_id")
    val consumerId = varchar("consumer_id", 255)
    val processedAt = datetime("processed_at")

    init {
        PrimaryKey(eventId, consumerId)
    }
}
