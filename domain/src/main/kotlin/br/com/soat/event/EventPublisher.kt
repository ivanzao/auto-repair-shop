package br.com.soat.event

import br.com.soat.event.model.DomainEvent

interface EventPublisher {
    fun publish(event: DomainEvent)
}