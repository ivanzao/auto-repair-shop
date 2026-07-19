package br.com.soat.event

import br.com.soat.event.model.EventStatus
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Outbox de eventos de saída sobre a tabela `events`: `type` guarda o eventType
 * lógico (ex. "OrderCreated") e `payload` o JSON do payload.
 */
class OutboxEventPostgresRepository : OutboxRepository {

    override fun save(event: OutboxEvent): OutboxEvent = transaction {
        Events.insert {
            it[id] = event.eventId
            it[createdAt] = LocalDateTime.ofInstant(event.occurredAt, ZoneOffset.UTC).toKotlinLocalDateTime()
            it[modifiedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            it[version] = event.eventVersion
            it[type] = event.eventType
            it[status] = event.status.name
            it[payload] = event.payload
        }
        event
    }

    override fun findPending(limit: Int): List<OutboxEvent> = transaction {
        Events.selectAll()
            .where { Events.status eq EventStatus.PENDING.name }
            .orderBy(Events.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                OutboxEvent(
                    eventId = row[Events.id],
                    eventType = row[Events.type],
                    eventVersion = row[Events.version],
                    occurredAt = row[Events.createdAt].toJavaLocalDateTime().toInstant(ZoneOffset.UTC),
                    payload = row[Events.payload],
                    status = EventStatus.valueOf(row[Events.status]),
                )
            }
    }

    override fun markPublished(eventId: UUID) {
        transaction {
            Events.update({ Events.id eq eventId }) {
                it[status] = EventStatus.PROCESSED.name
                it[modifiedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            }
        }
    }
}
