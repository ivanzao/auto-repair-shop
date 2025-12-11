package br.com.soat.shared

import br.com.soat.event.model.DomainEvent
import br.com.soat.event.model.EventStatus
import br.com.soat.event.repository.EventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class EventPostgresRepository : EventRepository {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    override fun save(event: DomainEvent): DomainEvent = transaction {
        Events.insert {
            it[id] = event.id
            it[createdAt] = event.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = event.modifiedAt.toKotlinLocalDateTime()
            it[version] = event.version
            it[type] = event::class.qualifiedName ?: "Unknown"
            it[status] = EventStatus.PENDING.name
            it[payload] = objectMapper.writeValueAsString(event)
        }
        event
    }

    override fun findPendingEvents(limit: Int): List<DomainEvent> = transaction {
        Events.selectAll()
            .where { Events.status eq EventStatus.PENDING.name }
            .orderBy(Events.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                val typeName = row[Events.type]
                val payload = row[Events.payload]
                val clazz = Class.forName(typeName).kotlin
                objectMapper.readValue(payload, clazz.java) as DomainEvent
            }
    }

    override fun findAllBy(type: String, status: EventStatus, limit: Int): List<DomainEvent> = transaction {
        Events.selectAll()
            .where { (Events.type like "%$type%") and (Events.status eq status.name) }
            .orderBy(Events.modifiedAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                val typeName = row[Events.type]
                val payload = row[Events.payload]
                val clazz = Class.forName(typeName).kotlin
                objectMapper.readValue(payload, clazz.java) as DomainEvent
            }
    }

    override fun existsByPayload(type: String, partialPayload: String): Boolean = transaction {
        Events.selectAll()
            .where { 
                (Events.type like "%$type%") and 
                (Events.payload like "%$partialPayload%") 
            }
            .count() > 0
    }

    override fun markAsProcessed(eventId: UUID, consumerId: String) {
        transaction {
            ProcessedEvents.insert {
                it[ProcessedEvents.eventId] = eventId
                it[ProcessedEvents.consumerId] = consumerId
                it[ProcessedEvents.processedAt] = java.time.LocalDateTime.now().toKotlinLocalDateTime()
            }
        }
    }

    override fun isProcessed(eventId: UUID, consumerId: String): Boolean = transaction {
        ProcessedEvents.selectAll()
            .where { 
                (ProcessedEvents.eventId eq eventId) and 
                (ProcessedEvents.consumerId eq consumerId) 
            }
            .count() > 0
    }

    override fun updateStatus(id: UUID, status: EventStatus) {
        transaction {
            Events.update({ Events.id eq id }) {
                it[Events.status] = status.name
            }
        }
    }
}
