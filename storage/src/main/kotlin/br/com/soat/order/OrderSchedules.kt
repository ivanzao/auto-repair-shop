package br.com.soat.order

import br.com.soat.order.model.OrderSchedule
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object OrderSchedules : Table("order_schedules") {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")

    val orderId = uuid("order_id")
    val scheduleDateTime = datetime("schedule_datetime")
    val type = varchar("type", 255)

    init {
        PrimaryKey(Orders.id)
    }
}

fun ResultRow.toOrderSchedule(): OrderSchedule = OrderSchedule(
    id = this[OrderSchedules.id],
    createdAt = this[OrderSchedules.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[OrderSchedules.modifiedAt].toJavaLocalDateTime(),
    orderId = this[OrderSchedules.orderId],
    dateTime = this[OrderSchedules.scheduleDateTime].toJavaLocalDateTime(),
    type = enumValueOf(this[OrderSchedules.type]),
)