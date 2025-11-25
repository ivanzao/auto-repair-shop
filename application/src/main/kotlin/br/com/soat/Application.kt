package br.com.soat

import EventDispatcherWorker
import br.com.soat.supply.SupplyUseCase
import br.com.soat.supply.SupplyRepository
import br.com.soat.user.UserUseCase
import br.com.soat.user.UserRepository
import br.com.soat.vehicle.VehicleUseCase
import br.com.soat.vehicle.VehicleRepository
import br.com.soat.config.Config
import br.com.soat.customer.CustomerPostgresRepository
import br.com.soat.customer.CustomerRepository
import br.com.soat.customer.CustomerUseCase
import br.com.soat.order.OrderPostgresRepository
import br.com.soat.order.OrderRepository
import br.com.soat.order.OrderUseCase
import br.com.soat.service.ServicePostgresRepository
import br.com.soat.service.ServiceRepository
import br.com.soat.shared.EventPostgresRepository
import br.com.soat.shared.repository.EventRepository
import br.com.soat.supply.SupplyPostgresRepository
import br.com.soat.user.UserPostgresRepository
import br.com.soat.vehicle.VehiclePostgresRepository
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import br.com.soat.worker.event.EventHandler
import br.com.soat.worker.command.CommandWorker
import br.com.soat.worker.command.CommandHandler
import br.com.soat.worker.event.handler.OrderDiagnoseFinishedEventHandler
import br.com.soat.worker.event.handler.SuppliesReservedEventHandler
import br.com.soat.worker.command.handler.SendQuoteToClientCommandHandler
import br.com.soat.shared.repository.CommandRepository
import br.com.soat.shared.CommandPostgresRepository
import br.com.soat.shared.repository.RepositoryTransactionManager
import br.com.soat.supply.service.SupplyStockService
import br.com.soat.transaction.PostgresTransactionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.dsl.bind

private val logger = LoggerFactory.getLogger("br.com.soat.ApplicationKt")

fun main() {
    logger.info("Starting Application")

    val config = Config.fromClasspath("application.yaml")
    logger.info("Loaded ${config.size()} configs from application.yaml")

    connectToDatabase(
        DatabaseConnectionParams(
            jdbcUrl = config.getString("database.url"),
            driverClassName = config.getString("database.driverClassName"),
            username = config.getString("database.username"),
            password = config.getString("database.password"),
            maximumPoolSize = config.getInt("database.maximumPoolSize")
        )
    )

    val koinApplication = startKoin {
        modules(applicationModule)
    }

    koinApplication.koin.get<CommandWorker>().start()
    koinApplication.koin.get<EventDispatcherWorker>().start()

    KtorHttpServer(
        koin = koinApplication.koin,
        port = config.getInt("server.port"),
        wait = true
    ).start()
}

val applicationModule = module {
    // storage
    single<UserRepository> { UserPostgresRepository() }
    single<SupplyRepository> { SupplyPostgresRepository() }
    single<VehicleRepository> { VehiclePostgresRepository() }
    single<CustomerRepository> { CustomerPostgresRepository() }
    single<OrderRepository> { OrderPostgresRepository() }
    single<ServiceRepository> { ServicePostgresRepository() }
    single<EventRepository> { EventPostgresRepository() }
    single<CommandRepository> { CommandPostgresRepository() }
    single<RepositoryTransactionManager> { PostgresTransactionManager() }

    // domain
    single<UserUseCase> { UserUseCase(get()) }
    single<SupplyUseCase> { SupplyUseCase(get()) }
    single<SupplyStockService>{ SupplyStockService(get(), get(), get(), get())}
    single<VehicleUseCase> { VehicleUseCase(get()) }
    single<CustomerUseCase> { CustomerUseCase(get()) }
    single<OrderUseCase> { OrderUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }

    // worker
    single<CoroutineDispatcher> { Dispatchers.IO }
    single { SendQuoteToClientCommandHandler() } bind CommandHandler::class
    single { OrderDiagnoseFinishedEventHandler(get()) } bind EventHandler::class
    single { SuppliesReservedEventHandler(get()) } bind EventHandler::class

    single { CommandWorker(get(), getAll(), get()) }
    single { EventDispatcherWorker(get(), getAll(), get())}
}