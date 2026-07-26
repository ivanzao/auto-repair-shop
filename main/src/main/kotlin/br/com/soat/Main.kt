package br.com.soat

import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.consumer.InboundEventConsumer
import br.com.soat.customer.CustomerPostgresRepository
import br.com.soat.customer.repository.CustomerRepository
import br.com.soat.customer.CustomerUseCase
import br.com.soat.consumer.InboundEventHandler
import br.com.soat.consumer.MessageQueue
import br.com.soat.consumer.SqsClient
import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEventPostgresRepository
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.idempotency.IdempotencyPostgresRepository
import br.com.soat.producer.EventEnvelopeSerializer
import br.com.soat.producer.OutboxRelay
import br.com.soat.producer.SnsClient
import br.com.soat.producer.SnsEventPublisher
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.order.OrderExecutionMetricPostgresRepository
import br.com.soat.order.OrderPostgresRepository
import br.com.soat.order.OrderSchedulePostgresRepository
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.OrderUseCase
import br.com.soat.consumer.handler.DiagnoseFinishedHandler
import br.com.soat.consumer.handler.ExecutionFinishedHandler
import br.com.soat.consumer.handler.ExecutionStartedHandler
import br.com.soat.consumer.handler.OrderCancellationHandler
import br.com.soat.consumer.handler.PaymentConfirmedHandler
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.scheduler.ScheduledTask
import br.com.soat.scheduler.ScheduledTaskRunner
import br.com.soat.scheduler.task.OutboxRelayTask
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.transaction.PostgresTransactionHandler
import br.com.soat.vehicle.VehiclePostgresRepository
import br.com.soat.vehicle.repository.VehicleRepository
import br.com.soat.vehicle.VehicleUseCase
import br.com.soat.config.prometheusMeterRegistry
import br.com.soat.metric.MicrometerOrderMetrics
import br.com.soat.order.OrderMetricsPort
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
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
    single<Config> { Config.fromClasspath("application.yaml") }
    single<Clock> { Clock.systemUTC() }
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<ObjectMapper> {
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
    }

    single<PrometheusMeterRegistry> { prometheusMeterRegistry() }
    single<MeterRegistry> { get<PrometheusMeterRegistry>() }
    single<OrderMetricsPort> { MicrometerOrderMetrics(get<MeterRegistry>()) }

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

    single<VehicleRepository> { VehiclePostgresRepository() }
    single<CustomerRepository> { CustomerPostgresRepository() }
    single<OrderRepository> { OrderPostgresRepository() }
    single<OrderScheduleRepository> { OrderSchedulePostgresRepository() }
    single<OrderExecutionMetricRepository> { OrderExecutionMetricPostgresRepository() }
    single<OutboxRepository> { OutboxEventPostgresRepository() }
    single<IdempotencyRepository> { IdempotencyPostgresRepository() }
    single<RepositoryTransactionHandler> { PostgresTransactionHandler() }

    single<VehicleUseCase> { VehicleUseCase(get()) }
    single<CustomerUseCase> { CustomerUseCase(get()) }
    single<OrderUseCase> { OrderUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { OrderListenerUseCase(get(), get(), get(), get(), get(), get()) }

    single { DiagnoseFinishedHandler(get()) } bind InboundEventHandler::class
    single { PaymentConfirmedHandler(get()) } bind InboundEventHandler::class
    single { ExecutionStartedHandler(get()) } bind InboundEventHandler::class
    single { ExecutionFinishedHandler(get()) } bind InboundEventHandler::class
    single { OrderCancellationHandler(get()) } bind InboundEventHandler::class

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

    single { EventEnvelopeSerializer(get()) }
    single<EventPublisher> { SnsEventPublisher(get(), get<SnsClient>(), get()) }
    single { OutboxRelay(get(), get()) }

    single { InboundEventConsumer(get(), getAll<InboundEventHandler>(), get(), get()) }

    single { OutboxRelayTask(get()) } bind ScheduledTask::class
    single { ScheduledTaskRunner(getAll()) }
}
