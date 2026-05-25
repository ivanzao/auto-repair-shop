package br.com.soat.messaging

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sns.model.MessageAttributeValue
import aws.sdk.kotlin.services.sns.model.PublishRequest
import aws.smithy.kotlin.runtime.net.url.Url
import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import kotlinx.coroutines.runBlocking

class SnsClient(
    private val topicArn: String,
    region: String = "us-east-1",
    endpointOverride: String? = null,
    accessKeyId: String? = null,
    secretAccessKey: String? = null,
) : AutoCloseable {

    private val client: AwsSnsClient = runBlocking {
        AwsSnsClient {
            this.region = region
            if (endpointOverride != null) {
                this.endpointUrl = Url.parse(endpointOverride)
            }
            if (accessKeyId != null && secretAccessKey != null) {
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = accessKeyId
                    this.secretAccessKey = secretAccessKey
                }
            }
        }
    }

    fun publish(payload: String, eventType: String, messageId: String) {
        runBlocking {
            client.publish(
                PublishRequest {
                    topicArn = this@SnsClient.topicArn
                    message = payload
                    messageAttributes = mapOf(
                        "event_type" to MessageAttributeValue {
                            dataType = "String"
                            stringValue = eventType
                        },
                        "message_id" to MessageAttributeValue {
                            dataType = "String"
                            stringValue = messageId
                        },
                    )
                },
            )
        }
    }

    override fun close() {
        client.close()
    }
}
