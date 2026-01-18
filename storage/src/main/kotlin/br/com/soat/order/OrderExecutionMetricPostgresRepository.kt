package br.com.soat.order

import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.repository.OrderExecutionMetricRepository
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class OrderExecutionMetricPostgresRepository : OrderExecutionMetricRepository {

    override fun save(metric: OrderExecutionMetric): OrderExecutionMetric = transaction {
        OrderExecutionMetrics.insert {
            it[id] = metric.id
            it[orderId] = metric.orderId
            it[inProgressAt] = metric.inProgressAt.toKotlinLocalDateTime()
            it[completedAt] = metric.completedAt?.toKotlinLocalDateTime()
        }.resultedValues?.singleOrNull()
            ?.toOrderExecutionMetric()
            ?: throw IllegalStateException("Failed to save OrderExecutionMetric")
    }

    override fun findByOrderId(orderId: UUID): OrderExecutionMetric? = transaction {
        OrderExecutionMetrics
            .selectAll()
            .where { OrderExecutionMetrics.orderId eq orderId }
            .limit(1)
            .firstOrNull()
            ?.toOrderExecutionMetric()
    }

    override fun update(metric: OrderExecutionMetric): OrderExecutionMetric = transaction {
        OrderExecutionMetrics.update({ OrderExecutionMetrics.id eq metric.id }) {
            it[completedAt] = metric.completedAt?.toKotlinLocalDateTime()
        }
        findByOrderId(metric.orderId) ?: throw IllegalStateException("Failed to update OrderExecutionMetric")
    }
}
