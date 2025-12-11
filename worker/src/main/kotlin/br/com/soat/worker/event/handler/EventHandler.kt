package br.com.soat.worker.event.handler

import br.com.soat.event.model.DomainEvent
import kotlin.reflect.KClass

interface EventHandler {
    val eventType: KClass<out DomainEvent>
    fun handle(event: DomainEvent)
}