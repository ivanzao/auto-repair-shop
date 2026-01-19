package br.com.soat.order.repository

import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.model.OrderMetrics
import java.util.UUID

interface OrderExecutionMetricRepository {
    fun save(metric: OrderExecutionMetric): OrderExecutionMetric
    fun findByOrderId(orderId: UUID): OrderExecutionMetric?
    fun update(metric: OrderExecutionMetric): OrderExecutionMetric
    fun getMetrics(): OrderMetrics
}
