package br.com.soat.worker.event

import br.com.soat.shared.DomainEvent
import kotlin.reflect.KClass

interface EventHandler {
    val eventType: KClass<out DomainEvent>
    fun handle(event: DomainEvent)
}
