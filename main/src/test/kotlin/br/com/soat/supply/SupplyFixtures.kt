package br.com.soat.supply

import br.com.soat.IntegrationTest
import br.com.soat.supply.model.Supply
import br.com.soat.supply.repository.SupplyRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

fun IntegrationTest.createSupply(
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    name: String = "Tire",
    description: String? = null,
    quantityInStock: Int = 10,
    price: BigDecimal = BigDecimal.TEN
) = get<SupplyRepository>().create(
    Supply(
        id = id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        version = version,
        name = name,
        description = description,
        quantityInStock = quantityInStock,
        price = price
    )
)