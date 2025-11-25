package br.com.soat.service

import br.com.soat.service.model.Service
import java.util.UUID

interface ServiceRepository {
    fun create(service: Service): Service
    fun update(service: Service): Service
    fun findById(id: UUID): Service?
    fun findAllByIds(servicesIds: List<UUID>): List<Service>
}