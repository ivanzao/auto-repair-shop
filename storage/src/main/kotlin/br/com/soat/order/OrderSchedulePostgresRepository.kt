package br.com.soat.order

import br.com.soat.order.model.OrderSchedule
import br.com.soat.order.repository.OrderScheduleRepository
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class OrderSchedulePostgresRepository: OrderScheduleRepository {

    override fun findAllByOrderId(orderId: UUID) = transaction {
        OrderSchedules.selectAll()
            .where { OrderSchedules.orderId eq orderId }
            .map { it.toOrderSchedule() }
    }

    override fun save(orderSchedule: OrderSchedule) = transaction {
        OrderSchedules.insert {
            it[id] = orderSchedule.id
            it[createdAt] = orderSchedule.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = orderSchedule.modifiedAt.toKotlinLocalDateTime()
            it[orderId] = orderSchedule.orderId
            it[scheduleDateTime] = orderSchedule.dateTime.toKotlinLocalDateTime()
            it[type] = orderSchedule.type.name
        }.resultedValues?.singleOrNull()
            ?.toOrderSchedule()
            ?: throw IllegalStateException("An error occurred while saving OrderSchedule")
    }
}