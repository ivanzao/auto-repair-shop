package br.com.soat

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import aws.sdk.kotlin.services.sns.model.CreateTopicRequest
import aws.sdk.kotlin.services.sns.model.SubscribeRequest
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueAttributesRequest
import aws.sdk.kotlin.services.sqs.model.PurgeQueueRequest
import aws.sdk.kotlin.services.sqs.model.QueueAttributeName
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.smithy.kotlin.runtime.net.url.Url
import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.consumer.CommandConsumerWorker
import br.com.soat.consumer.EventConsumerWorker
import br.com.soat.scheduler.ScheduledTaskRunner
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.ServerSocket
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1").withReuse(true)!!
    protected val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
        .withServices(SNS, SQS)
        .withReuse(true)

    lateinit var koinApplication: KoinApplication
    lateinit var server: KtorHttpServer
    lateinit var http: IntegrationTestHttpClient
    lateinit var sqsClient: SqsClient
    lateinit var queueUrl: String

    val httpClient: IntegrationTestHttpClient
        get() = http

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            exec("""
                DO ${'$'}${'$'} DECLARE
                    r RECORD;
                BEGIN
                    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                    END LOOP;
                END ${'$'}${'$'};
            """.trimIndent())
        }
        runBlocking {
            sqsClient.purgeQueue(PurgeQueueRequest { queueUrl = this@IntegrationTest.queueUrl })
        }
    }

    @BeforeAll
    open fun setup() {
        postgresContainer.start()
        localstack.start()

        val endpoint = localstack.getEndpointOverride(SNS).toString()
        val (topicArn, qUrl) = createSnsQueueWithSubscription(endpoint)
        queueUrl = qUrl

        val config = Config.fromClasspath("application-test.yaml").apply {
            put("database.url", postgresContainer.jdbcUrl)
            put("database.username", postgresContainer.username)
            put("database.password", postgresContainer.password)
            put("database.driverClassName", postgresContainer.driverClassName)
            put("server.port", serverPort)
            put("sns.topic.arn", topicArn)
            put("aws.endpoint", endpoint)
            put("aws.region", localstack.region)
            put("aws.accessKeyId", localstack.accessKey)
            put("aws.secretAccessKey", localstack.secretKey)
        }

        val dataSource = connectToDatabase(config)

        koinApplication = startKoin { modules(applicationModule, testModule(config)) }

        http = IntegrationTestHttpClient(serverPort)

        sqsClient = runBlocking {
            SqsClient {
                this.region = localstack.region
                this.endpointUrl = Url.parse(endpoint)
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = localstack.accessKey
                    this.secretAccessKey = localstack.secretKey
                }
            }
        }

        get<EventConsumerWorker>().start()
        get<CommandConsumerWorker>().start()

        get<ScheduledTaskRunner>().start(dataSource)

        server = KtorHttpServer(
            koin = koinApplication.koin,
            port = serverPort,
            wait = false
        )
        server.start()
    }

    private fun createSnsQueueWithSubscription(endpoint: String): Pair<String, String> = runBlocking {
        val sns = AwsSnsClient {
            this.region = localstack.region
            this.endpointUrl = Url.parse(endpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localstack.accessKey
                this.secretAccessKey = localstack.secretKey
            }
        }
        val sqs = SqsClient {
            this.region = localstack.region
            this.endpointUrl = Url.parse(endpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localstack.accessKey
                this.secretAccessKey = localstack.secretKey
            }
        }

        val topicArn = sns.createTopic(CreateTopicRequest { name = "auto-repair-shop-events-test" }).topicArn!!
        val createdQueue = sqs.createQueue(CreateQueueRequest { queueName = "email-queue-test" })
        val qUrl = createdQueue.queueUrl!!
        val queueArn = sqs.getQueueAttributes(GetQueueAttributesRequest {
            this.queueUrl = qUrl
            attributeNames = listOf(QueueAttributeName.QueueArn)
        }).attributes!![QueueAttributeName.QueueArn]!!

        sns.subscribe(SubscribeRequest {
            this.topicArn = topicArn
            protocol = "sqs"
            this.endpoint = queueArn
            attributes = mapOf(
                "RawMessageDelivery" to "true",
                "FilterPolicy" to """{"event_type":["SendQuoteEmailCommand"]}""",
            )
        })

        sns.close()
        sqs.close()
        topicArn to qUrl
    }

    @AfterAll
    fun tearDown() {
        server.stop()
        postgresContainer.stop()
        get<ScheduledTaskRunner>().stop()
        sqsClient.close()
        stopKoin()
    }

    inline fun <reified T> get(): T = koinApplication.koin.get()

    protected fun adminHeaders(userId: UUID = UUID.randomUUID()): Map<String, String> =
        mapOf("Authorization" to "Bearer ${fakeJwt(userId = userId, role = "ADMIN")}")

    protected fun attendantHeaders(userId: UUID = UUID.randomUUID()): Map<String, String> =
        mapOf("Authorization" to "Bearer ${fakeJwt(userId = userId, role = "ATTENDANT")}")

    private fun fakeJwt(userId: UUID, role: String): String {
        val header = b64u("""{"alg":"none","typ":"JWT"}""")
        val payload = b64u("""{"sub":"$userId","role":"$role","exp":9999999999}""")
        return "$header.$payload.test"
    }

    private fun b64u(s: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    protected fun waitForSnsMessage(eventType: String, timeoutSeconds: Long = 5): JsonNode {
        val mapper = get<ObjectMapper>()
        val deadline = Instant.now().plusSeconds(timeoutSeconds)
        while (Instant.now().isBefore(deadline)) {
            val msg = runBlocking {
                sqsClient.receiveMessage(ReceiveMessageRequest {
                    queueUrl = this@IntegrationTest.queueUrl
                    maxNumberOfMessages = 1
                    waitTimeSeconds = 1
                }).messages?.firstOrNull()
            }
            if (msg != null) {
                val payload = mapper.readTree(msg.body)
                runBlocking {
                    sqsClient.deleteMessage(DeleteMessageRequest {
                        queueUrl = this@IntegrationTest.queueUrl
                        receiptHandle = msg.receiptHandle
                    })
                }
                return payload
            }
        }
        throw AssertionError("Timeout waiting for SNS message with event_type=$eventType")
    }

    private fun testModule(config: Config) = module {
        single<Config> { config }
    }
}
