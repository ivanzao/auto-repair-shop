package br.com.soat.order.model

import net.logstash.logback.argument.StructuredArguments.kv

fun Order.logParams(vararg extra: Any): Array<Any> =
    arrayOf<Any>(
        kv("orderId", id),
        kv("customerId", customer.id),
        kv("vehicleId", vehicle.id),
        kv("attendantId", attendantId),
        kv("status", status.name),
    ).plus(elements = extra)
