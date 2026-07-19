package br.com.soat.event

import br.com.soat.event.ProcessedEventStore
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/** Idempotência de eventos de entrada sobre a tabela `processed_events`. */
class ProcessedEventPostgresStore : ProcessedEventStore {

    override fun isProcessed(eventId: UUID, consumerId: String): Boolean = transaction {
        ProcessedEvents.selectAll()
            .where { (ProcessedEvents.eventId eq eventId) and (ProcessedEvents.consumerId eq consumerId) }
            .count() > 0
    }

    override fun markAsProcessed(eventId: UUID, consumerId: String) {
        transaction {
            ProcessedEvents.insert {
                it[ProcessedEvents.eventId] = eventId
                it[ProcessedEvents.consumerId] = consumerId
                it[processedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            }
        }
    }
}
