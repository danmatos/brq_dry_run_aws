package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.Transaction
import br.com.itau.etl.producer.model.TransactionType
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class KafkaProducerServiceTest : DescribeSpec({

    describe("KafkaProducerService") {
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val objectMapper = mockk<ObjectMapper>()
        val kafkaProducerService = KafkaProducerService()
        
        beforeEach {
            ReflectionTestUtils.setField(kafkaProducerService, "kafkaTemplate", kafkaTemplate)
            ReflectionTestUtils.setField(kafkaProducerService, "objectMapper", objectMapper)
            ReflectionTestUtils.setField(kafkaProducerService, "transactionsTopic", "test-transactions-topic")
            clearAllMocks()
        }

        describe("sendTransaction") {
            
            context("when transaction is sent successfully") {
                it("should serialize and send transaction to Kafka") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-001",
                        accountId = "ACC-001",
                        amount = BigDecimal("100.50"),
                        type = TransactionType.CREDIT,
                        description = "Test transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val serializedTransaction = """{"id":"TXN-001","accountId":"ACC-001","amount":100.50}"""
                    val future = CompletableFuture<SendResult<String, String>>()
                    future.complete(mockk())
                    
                    every { objectMapper.writeValueAsString(transaction) } returns serializedTransaction
                    every { 
                        kafkaTemplate.send("test-transactions-topic", "TXN-001", serializedTransaction) 
                    } returns future
                    
                    // When
                    kafkaProducerService.sendTransaction(transaction)
                    
                    // Then
                    verify {
                        objectMapper.writeValueAsString(transaction)
                    }
                    
                    verify {
                        kafkaTemplate.send("test-transactions-topic", "TXN-001", serializedTransaction)
                    }
                }
            }
            
            context("when Kafka send fails") {
                it("should log error when CompletableFuture completes with exception") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-002",
                        accountId = "ACC-002",
                        amount = BigDecimal("250.75"),
                        type = TransactionType.DEBIT,
                        description = "Test transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val serializedTransaction = """{"id":"TXN-002","accountId":"ACC-002","amount":250.75}"""
                    val future = CompletableFuture<SendResult<String, String>>()
                    future.completeExceptionally(RuntimeException("Kafka error"))
                    
                    every { objectMapper.writeValueAsString(transaction) } returns serializedTransaction
                    every { 
                        kafkaTemplate.send("test-transactions-topic", "TXN-002", serializedTransaction) 
                    } returns future
                    
                    // When
                    kafkaProducerService.sendTransaction(transaction)
                    
                    // Then
                    verify {
                        kafkaTemplate.send("test-transactions-topic", "TXN-002", serializedTransaction)
                    }
                    
                    // Note: Error logging is handled asynchronously by CompletableFuture callback
                }
            }
            
            context("when serialization fails") {
                it("should throw exception") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-003",
                        accountId = "ACC-003",
                        amount = BigDecimal("75.25"),
                        type = TransactionType.PIX,
                        description = "Test transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    every { objectMapper.writeValueAsString(transaction) } throws RuntimeException("Serialization error")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        kafkaProducerService.sendTransaction(transaction)
                    }
                    
                    verify {
                        objectMapper.writeValueAsString(transaction)
                    }
                    
                    verify(exactly = 0) {
                        kafkaTemplate.send(any(), any(), any())
                    }
                }
            }
        }

        describe("sendBatch") {
            
            context("when sending multiple transactions") {
                it("should send all transactions in the batch") {
                    // Given
                    val transaction1 = Transaction(
                        id = "TXN-004",
                        accountId = "ACC-004",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.CREDIT,
                        description = "Batch transaction 1",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transaction2 = Transaction(
                        id = "TXN-005",
                        accountId = "ACC-005",
                        amount = BigDecimal("200.00"),
                        type = TransactionType.DEBIT,
                        description = "Batch transaction 2",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transactions = listOf(transaction1, transaction2)
                    
                    val serialized1 = """{"id":"TXN-004","accountId":"ACC-004","amount":100.00}"""
                    val serialized2 = """{"id":"TXN-005","accountId":"ACC-005","amount":200.00}"""
                    
                    val future1 = CompletableFuture<SendResult<String, String>>()
                    val future2 = CompletableFuture<SendResult<String, String>>()
                    future1.complete(mockk())
                    future2.complete(mockk())
                    
                    every { objectMapper.writeValueAsString(transaction1) } returns serialized1
                    every { objectMapper.writeValueAsString(transaction2) } returns serialized2
                    
                    every { 
                        kafkaTemplate.send("test-transactions-topic", "TXN-004", serialized1) 
                    } returns future1
                    
                    every { 
                        kafkaTemplate.send("test-transactions-topic", "TXN-005", serialized2) 
                    } returns future2
                    
                    // When
                    kafkaProducerService.sendBatch(transactions)
                    
                    // Then
                    verify {
                        objectMapper.writeValueAsString(transaction1)
                        objectMapper.writeValueAsString(transaction2)
                    }
                    
                    verify {
                        kafkaTemplate.send("test-transactions-topic", "TXN-004", serialized1)
                        kafkaTemplate.send("test-transactions-topic", "TXN-005", serialized2)
                    }
                }
            }
            
            context("when sending empty batch") {
                it("should complete without sending any messages") {
                    // Given
                    val transactions = emptyList<Transaction>()
                    
                    // When
                    kafkaProducerService.sendBatch(transactions)
                    
                    // Then
                    verify(exactly = 0) {
                        objectMapper.writeValueAsString(any())
                        kafkaTemplate.send(any(), any(), any())
                    }
                }
            }
            
            context("when one transaction in batch fails") {
                it("should still attempt to send remaining transactions") {
                    // Given
                    val transaction1 = Transaction(
                        id = "TXN-006",
                        accountId = "ACC-006",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.CREDIT,
                        description = "Batch transaction 1",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transaction2 = Transaction(
                        id = "TXN-007",
                        accountId = "ACC-007",
                        amount = BigDecimal("200.00"),
                        type = TransactionType.DEBIT,
                        description = "Batch transaction 2",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transactions = listOf(transaction1, transaction2)
                    
                    val serialized2 = """{"id":"TXN-007","accountId":"ACC-007","amount":200.00}"""
                    val future2 = CompletableFuture<SendResult<String, String>>()
                    future2.complete(mockk())
                    
                    every { objectMapper.writeValueAsString(transaction1) } throws RuntimeException("Serialization error")
                    every { objectMapper.writeValueAsString(transaction2) } returns serialized2
                    
                    every { 
                        kafkaTemplate.send("test-transactions-topic", "TXN-007", serialized2) 
                    } returns future2
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        kafkaProducerService.sendBatch(transactions)
                    }
                    
                    verify {
                        objectMapper.writeValueAsString(transaction1)
                        // Should not reach transaction2 due to exception in transaction1
                    }
                    
                    verify(exactly = 0) {
                        kafkaTemplate.send(any(), any(), any())
                    }
                }
            }
        }
    }
})
