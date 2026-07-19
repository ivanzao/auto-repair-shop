package br.com.soat

import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.attendant.AttendantPostgresRepository
import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.AttendantUseCase
import br.com.soat.consumer.InboundEventConsumer
import br.com.soat.customer.CustomerPostgresRepository
import br.com.soat.customer.CustomerRepository
import br.com.soat.customer.CustomerUseCase
import br.com.soat.event.InboundEventDispatcher
import br.com.soat.event.InboundEventHandler
import br.com.soat.event.OutboxEventPostgresRepository
import br.com.soat.event.OutboxRepository
import br.com.soat.event.ProcessedEventPostgresStore
import br.com.soat.event.ProcessedEventStore
import br.com.soat.messaging.MessageQueue
import br.com.soat.messaging.OutboxRelay
import br.com.soat.messaging.SnsClient
import br.com.soat.messaging.SqsClient
import br.com.soat.order.OrderExecutionMetricPostgresRepository
import br.com.soat.order.OrderPostgresRepository
import br.com.soat.order.OrderSchedulePostgresRepository
import br.com.soat.order.OrderStatusUseCase
import br.com.soat.order.OrderUseCase
import br.com.soat.order.event.handler.ExecutionFinishedHandler
import br.com.soat.order.event.handler.ExecutionProgressHandler
import br.com.soat.order.event.handler.OrderCancellationHandler
import br.com.soat.order.event.handler.PaymentConfirmedHandler
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.scheduler.ScheduledTask
import br.com.soat.scheduler.ScheduledTaskRunner
import br.com.soat.scheduler.task.OutboxRelayTask
import br.com.soat.service.ServicePostgresRepository
import br.com.soat.service.ServiceUseCase
import br.com.soat.service.repository.ServiceRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
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

    koinApplication.koin.get<InboundEventConsumer>().start()
    logger.info("Inbound event consumer started")

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
    single<VehicleRepository> { VehiclePostgresRepository() }
    single<CustomerRepository> { CustomerPostgresRepository() }
    single<OrderRepository> { OrderPostgresRepository(get()) }
    single<ServiceRepository> { ServicePostgresRepository() }
    single<OrderScheduleRepository> { OrderSchedulePostgresRepository() }
    single<OrderExecutionMetricRepository> { OrderExecutionMetricPostgresRepository() }
    single<OutboxRepository> { OutboxEventPostgresRepository() }
    single<ProcessedEventStore> { ProcessedEventPostgresStore() }
    single<RepositoryTransactionHandler> { PostgresTransactionHandler() }

    // domain
    single<AttendantUseCase> { AttendantUseCase(get()) }
    single<ServiceUseCase> { ServiceUseCase(get()) }
    single<VehicleUseCase> { VehicleUseCase(get()) }
    single<CustomerUseCase> { CustomerUseCase(get()) }
    single<OrderUseCase> { OrderUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { OrderStatusUseCase(get(), get()) }

    // inbound event handlers (billing/execution -> order status)
    single { PaymentConfirmedHandler(get()) } bind InboundEventHandler::class
    single { ExecutionFinishedHandler(get()) } bind InboundEventHandler::class
    single { OrderCancellationHandler(get()) } bind InboundEventHandler::class
    single { ExecutionProgressHandler(get()) } bind InboundEventHandler::class

    // inbound queue (SQS)
    single<MessageQueue> {
        val cfg = get<Config>()
        SqsClient(
            queueUrl = cfg.getString("sqs.queue.url"),
            region = cfg.getStringOrNull("aws.region") ?: "us-east-1",
            endpointOverride = cfg.getStringOrNull("aws.endpoint"),
            accessKeyId = cfg.getStringOrNull("aws.accessKeyId"),
            secretAccessKey = cfg.getStringOrNull("aws.secretAccessKey"),
        )
    }

    // event dispatch (inbound)
    single { InboundEventDispatcher(get(), getAll<InboundEventHandler>()) }

    // outbound relay (outbox -> SNS)
    single { OutboxRelay(get(), get(), get()) }

    // inbound consumer (SQS -> dispatcher)
    single { InboundEventConsumer(get(), get(), get(), get()) }

    // scheduled tasks
    single { OutboxRelayTask(get()) } bind ScheduledTask::class
    single { ScheduledTaskRunner(getAll()) }
}
