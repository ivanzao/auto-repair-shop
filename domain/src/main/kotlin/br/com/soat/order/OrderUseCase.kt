package br.com.soat.br.com.soat.order

import br.com.soat.br.com.soat.customer.Customer
import br.com.soat.br.com.soat.order.model.CreateOrderRequest
import br.com.soat.br.com.soat.order.model.Order
import br.com.soat.br.com.soat.user.model.User
import br.com.soat.br.com.soat.vehicle.Vehicle
import java.util.UUID

class OrderUseCase {

    fun create(request: CreateOrderRequest): Order {
        val customer = Customer(
            name = "Freddy Le",
            document = "solum",
            email = "leslie.ewing@example.com",
            contact = "class"
        )

        val vehicle = Vehicle(
            clientId = UUID.randomUUID(),
            plate = "expetenda",
            brand = "persius",
            model = "dicant",
            year = 2022
        )

        val attendant = User(
            name = "Tania Mueller",
            document = "verterem",
            email = "michelle.camacho@example.com",
            contact = "assueverit",
            role = User.Role.ATTENDANT
        )

        return Order(
            customer = customer,
            vehicle = vehicle,
            attendant = attendant,
            description = request.description,
            services = emptyList(),
            technician = "Julio Cesar"
        )
    }
}