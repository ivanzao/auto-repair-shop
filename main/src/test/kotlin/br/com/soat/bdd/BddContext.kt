package br.com.soat.bdd

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import aws.sdk.kotlin.services.sns.model.CreateTopicRequest
import aws.sdk.kotlin.services.sns.model.SubscribeRequest
import aws.sdk.kotlin.services.sqs.SqsClient as AwsSqsClient
import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueAttributesRequest
import aws.sdk.kotlin.services.sqs.model.PurgeQueueRequest
import aws.sdk.kotlin.services.sqs.model.QueueAttributeName
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest
import aws.smithy.kotlin.runtime.net.url.Url
import br.com.soat.applicationModule
import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.connectToDatabase
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import org.testcontainers.utility.DockerImageName

/**
 * Infra compartilhada do BDD: sobe Postgres + LocalStack e o app uma única vez.
 * O relay e o consumidor são acionados de forma síncrona (sem schedulers) para
 * cenários determinísticos. billing/execution são simulados publicando envelopes
 * direto na fila de entrada.
 */
object BddContext {

    private val postgres = PostgreSQLContainer("postgres:18.1").withReuse(true)
    private val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
        .withServices(SNS, SQS)
        .withReuse(true)

    lateinit var koin: KoinApplication
    private lateinit var sqs: AwsSqsClient
    private lateinit var topicArn: String
    private lateinit var inboundQueueUrl: String
    private lateinit var spyQueueUrl: String
    private var started = false

    inline fun <reified T> get(): T = koin.koin.get()

    fun start() {
        if (started) return
        postgres.start()
        localstack.start()
        val endpoint = localstack.getEndpointOverride(SNS).toString()

        val sns = awsSns(endpoint)
        sqs = awsSqs(endpoint)

        topicArn = runBlocking {
            sns.createTopic(CreateTopicRequest { name = "auto-repair-shop-order-events-test" }).topicArn!!
        }
        inboundQueueUrl = createQueue("auto-repair-shop-order-saga-test")
        spyQueueUrl = createQueue("order-events-spy-test")
        subscribeRaw(sns, topicArn, spyQueueUrl)
        runBlocking { sns.close() }

        val config = Config.fromClasspath("application-test.yaml").apply {
            put("database.url", postgres.jdbcUrl)
            put("database.username", postgres.username)
            put("database.password", postgres.password)
            put("database.driverClassName", postgres.driverClassName)
            put("server.port", 0)
            put("sns.topic.arn", topicArn)
            put("sqs.queue.url", inboundQueueUrl)
            put("aws.endpoint", endpoint)
            put("aws.region", localstack.region)
            put("aws.accessKeyId", localstack.accessKey)
            put("aws.secretAccessKey", localstack.secretKey)
        }
        connectToDatabase(config)
        // KoinApplication isolado (não global) para não colidir com o startKoin
        // dos demais testes de integração que rodam no mesmo JVM.
        koin = koinApplication { modules(applicationModule, module { single<Config> { config } }) }
        started = true
    }

    fun reset() {
        transaction {
            exec(
                """
                DO ${'$'}${'$'} DECLARE r RECORD;
                BEGIN
                  FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                  END LOOP;
                END ${'$'}${'$'};
                """.trimIndent()
            )
        }
        runBlocking {
            sqs.purgeQueue(PurgeQueueRequest { queueUrl = inboundQueueUrl })
            sqs.purgeQueue(PurgeQueueRequest { queueUrl = spyQueueUrl })
        }
    }

    /** Simula billing/execution publicando um envelope na fila de entrada do order. */
    fun sendInbound(envelopeJson: String) {
        runBlocking {
            sqs.sendMessage(SendMessageRequest { queueUrl = inboundQueueUrl; messageBody = envelopeJson })
        }
    }

    /** Recebe um envelope publicado no tópico de eventos do order (via fila espiã). */
    fun receiveFromTopic(mapper: com.fasterxml.jackson.databind.ObjectMapper, timeoutMs: Long = 5000): JsonNode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val msg = runBlocking {
                sqs.receiveMessage(ReceiveMessageRequest {
                    queueUrl = spyQueueUrl; maxNumberOfMessages = 1; waitTimeSeconds = 1
                }).messages?.firstOrNull()
            }
            if (msg != null) {
                runBlocking {
                    sqs.deleteMessage(DeleteMessageRequest { queueUrl = spyQueueUrl; receiptHandle = msg.receiptHandle })
                }
                return mapper.readTree(msg.body)
            }
        }
        return null
    }

    private fun createQueue(name: String): String = runBlocking {
        sqs.createQueue(CreateQueueRequest { queueName = name }).queueUrl!!
    }

    private fun subscribeRaw(sns: AwsSnsClient, topic: String, queueUrl: String) = runBlocking {
        val arn = sqs.getQueueAttributes(GetQueueAttributesRequest {
            this.queueUrl = queueUrl
            attributeNames = listOf(QueueAttributeName.QueueArn)
        }).attributes!![QueueAttributeName.QueueArn]!!
        sns.subscribe(SubscribeRequest {
            topicArn = topic
            protocol = "sqs"
            endpoint = arn
            attributes = mapOf("RawMessageDelivery" to "true")
        })
    }

    private fun awsSns(endpoint: String) = AwsSnsClient {
        region = localstack.region
        endpointUrl = Url.parse(endpoint)
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = localstack.accessKey; secretAccessKey = localstack.secretKey
        }
    }

    private fun awsSqs(endpoint: String) = AwsSqsClient {
        region = localstack.region
        endpointUrl = Url.parse(endpoint)
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = localstack.accessKey; secretAccessKey = localstack.secretKey
        }
    }
}
