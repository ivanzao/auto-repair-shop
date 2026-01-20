package br.com.soat.order.repository

import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.model.OrderMetrics
import java.util.UUID

interface OrderExecutionMetricRepository {
    fun findByOrderId(orderId: UUID): OrderExecutionMetric?
    fun getMetrics(): OrderMetrics
    fun create(metric: OrderExecutionMetric): OrderExecutionMetric
    fun update(metric: OrderExecutionMetric): OrderExecutionMetric
}
