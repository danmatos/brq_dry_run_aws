package br.com.itau.etl.producer.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.msk.auth.iam.IAMClientCallbackHandler

class KafkaProducerConfigTest : DescribeSpec({

    describe("KafkaProducerConfig") {
        val kafkaProducerConfig = KafkaProducerConfig()
        
        beforeEach {
            ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", "localhost:9092")
        }

        describe("producerFactory") {
            context("when creating producer factory") {
                it("should create DefaultKafkaProducerFactory with correct configuration") {
                    // When
                    val producerFactory = kafkaProducerConfig.producerFactory()
                    
                    // Then
                    producerFactory shouldNotBe null
                    producerFactory.shouldBeInstanceOf<DefaultKafkaProducerFactory<String, String>>()
                    
                    val configurationProperties = producerFactory.configurationProperties
                    configurationProperties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] shouldBe "localhost:9092"
                    configurationProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] shouldBe StringSerializer::class.java
                    configurationProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] shouldBe StringSerializer::class.java
                    configurationProperties[ProducerConfig.ACKS_CONFIG] shouldBe "all"
                    configurationProperties[ProducerConfig.RETRIES_CONFIG] shouldBe 3
                    configurationProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] shouldBe true
                }
            }
            
            context("when creating producer factory with AWS MSK IAM authentication") {
                it("should configure SASL_SSL and AWS_MSK_IAM mechanism") {
                    // When
                    val producerFactory = kafkaProducerConfig.producerFactory()
                    
                    // Then
                    val configurationProperties = producerFactory.configurationProperties
                    configurationProperties["security.protocol"] shouldBe "SASL_SSL"
                    configurationProperties["sasl.mechanism"] shouldBe "AWS_MSK_IAM"
                    configurationProperties["sasl.jaas.config"] shouldBe "software.amazon.msk.auth.iam.IAMLoginModule required;"
                    configurationProperties["sasl.client.callback.handler.class"] shouldBe IAMClientCallbackHandler::class.java.name
                }
            }
            
            context("when creating producer factory with different bootstrap servers") {
                it("should use the configured bootstrap servers") {
                    // Given
                    ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", "kafka-cluster:9092")
                    
                    // When
                    val producerFactory = kafkaProducerConfig.producerFactory()
                    
                    // Then
                    val configurationProperties = producerFactory.configurationProperties
                    configurationProperties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] shouldBe "kafka-cluster:9092"
                }
            }
        }

        describe("kafkaTemplate") {
            context("when creating Kafka template") {
                it("should create KafkaTemplate with producer factory") {
                    // When
                    val kafkaTemplate = kafkaProducerConfig.kafkaTemplate()
                    
                    // Then
                    kafkaTemplate shouldNotBe null
                    kafkaTemplate.shouldBeInstanceOf<KafkaTemplate<String, String>>()
                    kafkaTemplate.producerFactory shouldNotBe null
                }
            }
            
            context("when verifying Kafka template integration") {
                it("should have producer factory with correct configuration") {
                    // When
                    val kafkaTemplate = kafkaProducerConfig.kafkaTemplate()
                    
                    // Then
                    val producerFactory = kafkaTemplate.producerFactory
                    producerFactory.shouldBeInstanceOf<DefaultKafkaProducerFactory<String, String>>()
                    
                    val configurationProperties = producerFactory.configurationProperties
                    configurationProperties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] shouldBe "localhost:9092"
                    configurationProperties[ProducerConfig.ACKS_CONFIG] shouldBe "all"
                    configurationProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] shouldBe true
                }
            }
        }
    }
})
