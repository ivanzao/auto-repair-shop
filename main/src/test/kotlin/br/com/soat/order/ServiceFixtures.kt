package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.service.model.Service
import br.com.soat.service.repository.ServiceRepository
import br.com.soat.supply.model.SupplyRequirement
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

fun IntegrationTest.createService(
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    requiredSupplies: List<SupplyRequirement> = emptyList(),
    name: String = "Generic Repair",
    description: String? = null,
    price: BigDecimal = BigDecimal.TEN
) = get<ServiceRepository>().create(
    Service(
        id = id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        version = version,
        requiredSupplies = requiredSupplies,
        name = name,
        description = description,
        price = price
    )
)