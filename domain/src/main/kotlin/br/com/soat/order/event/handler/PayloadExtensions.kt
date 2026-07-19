package br.com.soat.order.event.handler

import br.com.soat.event.EventEnvelope
import java.util.UUID

/** `orderId` é a chave de correlação presente em todo evento do fluxo. */
internal fun EventEnvelope.orderId(): UUID = UUID.fromString(payload.get("orderId").asText())
