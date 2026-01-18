package br.com.soat.order

import br.com.soat.order.model.OrderExecutionMetric
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object OrderExecutionMetrics : Table("order_execution_metrics") {
    val id = uuid("id")
    val orderId = uuid("order_id").references(Orders.id)
    val inProgressAt = datetime("in_progress_at")
    val completedAt = datetime("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toOrderExecutionMetric() = OrderExecutionMetric(
    id = this[OrderExecutionMetrics.id],
    orderId = this[OrderExecutionMetrics.orderId],
    inProgressAt = this[OrderExecutionMetrics.inProgressAt].toJavaLocalDateTime(),
    completedAt = this[OrderExecutionMetrics.completedAt]?.toJavaLocalDateTime()
)
