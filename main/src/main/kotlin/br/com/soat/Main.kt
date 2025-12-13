package br.com.soat

import br.com.soat.auth.LoginUseCase
import br.com.soat.auth.JWTAuthenticationTokenProvider
import br.com.soat.auth.RefreshTokenPostgresRepository
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.auth.port.RefreshTokenRepository
import br.com.soat.config.Config
import br.com.soat.customer.CustomerPostgresRepository
import br.com.soat.customer.CustomerRepository
import br.com.soat.customer.CustomerUseCase
import br.com.soat.order.OrderPostgresRepository
import br.com.soat.order.OrderRepository
import br.com.soat.order.OrderUseCase
import br.com.soat.security.HashService
import br.com.soat.service.ServicePostgresRepository
import br.com.soat.service.ServiceRepository
import br.com.soat.shared.CommandPostgresRepository
import br.com.soat.shared.EventPostgresRepository
import br.com.soat.command.repository.CommandRepository
import br.com.soat.event.repository.EventRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.SupplyPostgresRepository
import br.com.soat.supply.SupplyRepository
import br.com.soat.supply.SupplyUseCase
import br.com.soat.supply.service.SupplyStockService
import br.com.soat.transaction.PostgresTransactionHandler
import br.com.soat.user.UserPostgresRepository
import br.com.soat.user.UserRepository
import br.com.soat.user.UserUseCase
import br.com.soat.vehicle.VehiclePostgresRepository
import br.com.soat.vehicle.VehicleRepository
import br.com.soat.vehicle.VehicleUseCase
import br.com.soat.command.CommandHandler
import br.com.soat.command.CommandProcessor
import br.com.soat.config.fromClasspath
import br.com.soat.event.EventHandler
import br.com.soat.event.EventProcessor
import br.com.soat.worker.command.CommandProcessorWorker
import br.com.soat.worker.command.handler.SendQuoteToClientCommandHandler
import br.com.soat.worker.event.EventProcessorWorker
import br.com.soat.worker.event.handler.OrderDiagnoseFinishedEventHandler
import br.com.soat.worker.event.handler.SuppliesReservedEventHandler
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("br.com.soat.MainKt")

fun main() {
    logger.info("Starting auto-repair-shop application")

    val koinApplication = startKoin {
        modules(applicationModule)
    }

    val config  = koinApplication.koin.get<Config>()

    connectToDatabase(
        DatabaseConnectionParams(
            jdbcUrl = config.getString("database.url"),
            driverClassName = config.getString("database.driverClassName"),
            username = config.getString("database.username"),
            password = config.getString("database.password"),
            maximumPoolSize = config.getInt("database.maximumPoolSize")
        )
    )

    koinApplication.koin.get<CommandProcessorWorker>().start()
    koinApplication.koin.get<EventProcessorWorker>().start()

    KtorHttpServer(
        koin = koinApplication.koin,
        port = config.getInt("server.port"),
        wait = true
    ).start()
}

val applicationModule = module {
    //config
    single<Config> { Config.fromClasspath("application.yaml") }
    single<Clock> { Clock.systemUTC() }

    // storage
    single<UserRepository> { UserPostgresRepository() }
    single<SupplyRepository> { SupplyPostgresRepository() }
    single<VehicleRepository> { VehiclePostgresRepository() }
    single<CustomerRepository> { CustomerPostgresRepository() }
    single<OrderRepository> { OrderPostgresRepository(get()) }
    single<ServiceRepository> { ServicePostgresRepository() }
    single<EventRepository> { EventPostgresRepository() }
    single<CommandRepository> { CommandPostgresRepository() }
    single<RefreshTokenRepository> { RefreshTokenPostgresRepository() }
    single<RepositoryTransactionHandler> { PostgresTransactionHandler() }

    // domain
    single<HashService> { HashService(get()) }
    single<AuthenticationTokenProvider> { JWTAuthenticationTokenProvider(get(), get()) }
    single<LoginUseCase> { LoginUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single<UserUseCase> { UserUseCase(get(), get()) }
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

    single { CommandProcessor(get(), getAll()) }
    single { EventProcessor(get(), getAll()) }

    single { CommandProcessorWorker(get(), get()) }
    single { EventProcessorWorker(get(), get()) }
}