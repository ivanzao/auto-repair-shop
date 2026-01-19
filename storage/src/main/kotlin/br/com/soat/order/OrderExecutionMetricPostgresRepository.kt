package br.com.soat.order

import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.model.OrderMetrics
import br.com.soat.order.repository.OrderExecutionMetricRepository
import java.time.Duration
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

    override fun getMetrics(): OrderMetrics = transaction {
        val sql = """
            SELECT
                COUNT(*) as total_completed,
                AVG(EXTRACT(EPOCH FROM (completed_at - in_progress_at))) as avg_seconds
            FROM order_execution_metrics
            WHERE completed_at IS NOT NULL
        """.trimIndent()

        val result = exec(sql) { rs ->
            if (rs.next()) {
                val totalCompleted = rs.getLong("total_completed")
                val avgSeconds = rs.getDouble("avg_seconds").takeIf { !rs.wasNull() }
                totalCompleted to avgSeconds
            } else {
                0L to null
            }
        } ?: (0L to null)

        OrderMetrics(
            totalCompleted = result.first,
            averageExecutionTime = result.second?.let { Duration.ofSeconds(it.toLong()) }
        )
    }
}
