package br.com.soat

import br.com.soat.bus.CommandBus
import br.com.soat.bus.EventBus
import br.com.soat.command.CommandPostgresRepository
import br.com.soat.command.CommandProcessor
import br.com.soat.command.CommandPublisher
import br.com.soat.command.handler.CommandHandler
import br.com.soat.command.repository.CommandRepository
import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.attendant.AttendantPostgresRepository
import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.AttendantUseCase
import br.com.soat.consumer.CommandConsumerWorker
import br.com.soat.consumer.EventConsumerWorker
import br.com.soat.customer.CustomerPostgresRepository
import br.com.soat.customer.CustomerRepository
import br.com.soat.customer.CustomerUseCase
import br.com.soat.event.EventPostgresRepository
import br.com.soat.event.EventProcessor
import br.com.soat.event.EventPublisher
import br.com.soat.event.handler.EventHandler
import br.com.soat.event.repository.EventRepository
import br.com.soat.messaging.SnsClient
import br.com.soat.messaging.SnsRelayCommandHandler
import br.com.soat.order.OrderApprovalTokenPostgresRepository
import br.com.soat.order.OrderExecutionMetricPostgresRepository
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.OrderPostgresRepository
import br.com.soat.order.OrderSchedulePostgresRepository
import br.com.soat.order.OrderUseCase
import br.com.soat.order.event.handler.OrderCompletedEventHandler
import br.com.soat.order.event.handler.OrderInProgressEventHandler
import br.com.soat.order.event.handler.SuppliesReservedEventHandler
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.publisher.DefaultCommandPublisher
import br.com.soat.publisher.DefaultEventPublisher
import br.com.soat.scheduler.ScheduledTask
import br.com.soat.scheduler.ScheduledTaskRunner
import br.com.soat.scheduler.task.CommandProcessorTask
import br.com.soat.scheduler.task.EventProcessorTask
import br.com.soat.service.ServicePostgresRepository
import br.com.soat.service.ServiceUseCase
import br.com.soat.service.repository.ServiceRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.supply.SupplyPostgresRepository
import br.com.soat.supply.SupplyStockService
import br.com.soat.supply.SupplyUseCase
import br.com.soat.supply.model.event.handler.OrderDiagnoseFinishedEventHandler
import br.com.soat.supply.repository.SupplyRepository
import br.com.soat.transaction.PostgresTransactionHandler
import br.com.soat.vehicle.VehiclePostgresRepository
import br.com.soat.vehicle.VehicleRepository
import br.com.soat.vehicle.VehicleUseCase
import br.com.soat.config.prometheusMeterRegistry
import br.com.soat.metric.MetricsPort
import br.com.soat.metric.MicrometerMetricsPort
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
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

    val dataSource = connectToDatabase(config)

    koinApplication.koin.get<EventConsumerWorker>().start()
    koinApplication.koin.get<CommandConsumerWorker>().start()
    logger.info("Event and Command consumers started")

    koinApplication.koin.get<ScheduledTaskRunner>().start(dataSource)
    logger.info("ScheduledTaskRunner started")

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
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<ObjectMapper> { jacksonObjectMapper().registerModule(JavaTimeModule()) }

    // observability
    single<PrometheusMeterRegistry> { prometheusMeterRegistry() }
    single<MeterRegistry> { get<PrometheusMeterRegistry>() }
    single<MetricsPort> { MicrometerMetricsPort(get<MeterRegistry>()) }

    // messaging
    single {
        val cfg = get<Config>()
        SnsClient(
            topicArn = cfg.getString("sns.topic.arn"),
            region = cfg.getStringOrNull("aws.region") ?: "us-east-1",
            endpointOverride = cfg.getStringOrNull("aws.endpoint"),
            accessKeyId = cfg.getStringOrNull("aws.accessKeyId"),
            secretAccessKey = cfg.getStringOrNull("aws.secretAccessKey"),
        )
    }

    // storage
    single<AttendantRepository> { AttendantPostgresRepository() }
    single<SupplyRepository> { SupplyPostgresRepository() }
    single<VehicleRepository> { VehiclePostgresRepository() }
    single<CustomerRepository> { CustomerPostgresRepository() }
    single<OrderRepository> { OrderPostgresRepository(get()) }
    single<ServiceRepository> { ServicePostgresRepository() }
    single<OrderScheduleRepository> { OrderSchedulePostgresRepository() }
    single<OrderApprovalTokenRepository> { OrderApprovalTokenPostgresRepository() }
    single<OrderExecutionMetricRepository> { OrderExecutionMetricPostgresRepository() }
    single<EventRepository> { EventPostgresRepository() }
    single<CommandRepository> { CommandPostgresRepository() }
    single<RepositoryTransactionHandler> { PostgresTransactionHandler() }

    // domain
    single<AttendantUseCase> { AttendantUseCase(get()) }
    single<SupplyUseCase> { SupplyUseCase(get()) }
    single<ServiceUseCase> { ServiceUseCase(get()) }
    single<SupplyStockService> { SupplyStockService(get(), get(), get(), get(), get()) }
    single<VehicleUseCase> { VehicleUseCase(get()) }
    single<CustomerUseCase> { CustomerUseCase(get()) }
    single<OrderUseCase> { OrderUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<OrderListenerUseCase> { OrderListenerUseCase(get(), get(), get(), get(), get(), get(), get()) }

    // pubsub
    single { EventBus() }
    single { CommandBus() }

    // event and command handlers
    single { SnsRelayCommandHandler(get(), get()) } bind CommandHandler::class
    single { OrderDiagnoseFinishedEventHandler(get()) } bind EventHandler::class
    single { OrderInProgressEventHandler(get()) } bind EventHandler::class
    single { OrderCompletedEventHandler(get()) } bind EventHandler::class
    single { SuppliesReservedEventHandler(get()) } bind EventHandler::class

    single { CommandProcessor(get(), getAll()) }
    single { EventProcessor(get(), getAll()) }

    // pubsub publishers
    single<EventPublisher> { DefaultEventPublisher(get(), get()) }
    single<CommandPublisher> { DefaultCommandPublisher(get(), get()) }

    // pubsub consumers
    single { EventConsumerWorker(get(), get(), get()) }
    single { CommandConsumerWorker(get(), get(), get()) }

    // scheduled tasks
    single { CommandProcessorTask(get()) } bind ScheduledTask::class
    single { EventProcessorTask(get()) } bind ScheduledTask::class
    single { ScheduledTaskRunner(getAll()) }
}

