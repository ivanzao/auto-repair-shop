package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import java.util.UUID

internal fun EventEnvelope.orderId(): UUID = UUID.fromString(payload.get("orderId").asText())
