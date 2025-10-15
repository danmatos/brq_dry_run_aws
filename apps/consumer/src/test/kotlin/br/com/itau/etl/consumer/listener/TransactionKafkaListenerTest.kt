package br.com.itau.etl.consumer.listener

import br.com.itau.etl.consumer.model.*
import br.com.itau.etl.consumer.service.TransactionProcessorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import org.springframework.kafka.support.Acknowledgment
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionKafkaListenerTest : DescribeSpec({

    describe("TransactionKafkaListener") {
        val transactionProcessorService = mockk<TransactionProcessorService>()
        val acknowledgment = mockk<Acknowledgment>()
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
        }
        val transactionKafkaListener = TransactionKafkaListener()
        
        beforeEach {
            ReflectionTestUtils.setField(transactionKafkaListener, "transactionProcessorService", transactionProcessorService)
            ReflectionTestUtils.setField(transactionKafkaListener, "objectMapper", objectMapper)
            clearAllMocks()
        }

        describe("consumeTransaction") {
            val topic = "test-transactions-topic"
            val partition = 0
            val offset = 123L
            
            val transaction = Transaction(
                id = "txn-kafka-test",
                accountId = "acc-kafka-test",
                amount = BigDecimal("200.50"),
                type = TransactionType.PIX,
                description = "Kafka test transaction",
                timestamp = LocalDateTime.of(2025, 1, 14, 15, 45, 30),
                pixData = PixData(
                    pixKey = "kafka@test.com",
                    pixKeyType = PixKeyType.EMAIL,
                    endToEndId = "E98765432109876543210987654321098765"
                )
            )
            
            val processedTransaction = ProcessedTransaction(
                transactionId = transaction.id,
                accountId = transaction.accountId,
                amount = transaction.amount,
                type = transaction.type,
                description = transaction.description,
                timestamp = transaction.timestamp,
                processedAt = LocalDateTime.now(),
                pixData = transaction.pixData,
                status = TransactionStatus.PROCESSED
            )

            context("when consuming valid transaction message") {
                it("should process transaction and acknowledge") {
                    // Given
                    val message = objectMapper.writeValueAsString(transaction)
                    every { transactionProcessorService.processTransaction(transaction) } returns processedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { transactionProcessorService.processTransaction(match { it.id == transaction.id }) }
                    verify { acknowledgment.acknowledge() }
                }
            }
            
            context("when consuming transaction without PIX data") {
                it("should process TED transaction successfully") {
                    // Given
                    val tedTransaction = transaction.copy(type = TransactionType.TED, pixData = null)
                    val message = objectMapper.writeValueAsString(tedTransaction)
                    val tedProcessedTransaction = processedTransaction.copy(type = TransactionType.TED, pixData = null)
                    
                    every { transactionProcessorService.processTransaction(tedTransaction) } returns tedProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.type == TransactionType.TED && it.pixData == null }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
            }
            
            context("when processing different transaction types") {
                it("should handle CREDIT transaction") {
                    // Given
                    val creditTransaction = transaction.copy(type = TransactionType.CREDIT, pixData = null)
                    val message = objectMapper.writeValueAsString(creditTransaction)
                    val creditProcessedTransaction = processedTransaction.copy(type = TransactionType.CREDIT, pixData = null)
                    
                    every { transactionProcessorService.processTransaction(creditTransaction) } returns creditProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.type == TransactionType.CREDIT }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
                
                it("should handle DEBIT transaction") {
                    // Given
                    val debitTransaction = transaction.copy(type = TransactionType.DEBIT, pixData = null)
                    val message = objectMapper.writeValueAsString(debitTransaction)
                    val debitProcessedTransaction = processedTransaction.copy(type = TransactionType.DEBIT, pixData = null)
                    
                    every { transactionProcessorService.processTransaction(debitTransaction) } returns debitProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.type == TransactionType.DEBIT }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
            }
            
            context("when JSON deserialization fails") {
                it("should acknowledge message to avoid infinite retries") {
                    // Given
                    val invalidMessage = "{ invalid json }"
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(invalidMessage, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify(exactly = 0) { transactionProcessorService.processTransaction(any()) }
                    verify { acknowledgment.acknowledge() }
                }
            }
            
            context("when transaction processing fails") {
                it("should still acknowledge message to avoid infinite retries") {
                    // Given
                    val message = objectMapper.writeValueAsString(transaction)
                    every { transactionProcessorService.processTransaction(any()) } throws RuntimeException("Processing failed")
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { transactionProcessorService.processTransaction(any()) }
                    verify { acknowledgment.acknowledge() }
                }
            }
            
            context("when processing transaction with different PIX key types") {
                it("should handle CPF PIX key type") {
                    // Given
                    val cpfTransaction = transaction.copy(
                        pixData = PixData(
                            pixKey = "12345678901",
                            pixKeyType = PixKeyType.CPF,
                            endToEndId = "E12345678901234567890123456789012345"
                        )
                    )
                    val message = objectMapper.writeValueAsString(cpfTransaction)
                    val cpfProcessedTransaction = processedTransaction.copy(pixData = cpfTransaction.pixData)
                    
                    every { transactionProcessorService.processTransaction(cpfTransaction) } returns cpfProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.pixData?.pixKeyType == PixKeyType.CPF }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
                
                it("should handle PHONE PIX key type") {
                    // Given
                    val phoneTransaction = transaction.copy(
                        pixData = PixData(
                            pixKey = "11999887766",
                            pixKeyType = PixKeyType.PHONE,
                            endToEndId = "E98765432109876543210987654321098765"
                        )
                    )
                    val message = objectMapper.writeValueAsString(phoneTransaction)
                    val phoneProcessedTransaction = processedTransaction.copy(pixData = phoneTransaction.pixData)
                    
                    every { transactionProcessorService.processTransaction(phoneTransaction) } returns phoneProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.pixData?.pixKeyType == PixKeyType.PHONE }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
                
                it("should handle RANDOM PIX key type") {
                    // Given
                    val randomTransaction = transaction.copy(
                        pixData = PixData(
                            pixKey = "123e4567-e89b-12d3-a456-426614174000",
                            pixKeyType = PixKeyType.RANDOM,
                            endToEndId = "E11111111111111111111111111111111111"
                        )
                    )
                    val message = objectMapper.writeValueAsString(randomTransaction)
                    val randomProcessedTransaction = processedTransaction.copy(pixData = randomTransaction.pixData)
                    
                    every { transactionProcessorService.processTransaction(randomTransaction) } returns randomProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.pixData?.pixKeyType == PixKeyType.RANDOM }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
            }
            
            context("when consuming message with different amounts") {
                it("should handle large amount transaction") {
                    // Given
                    val largeAmountTransaction = transaction.copy(amount = BigDecimal("9999999.99"))
                    val message = objectMapper.writeValueAsString(largeAmountTransaction)
                    val largeAmountProcessedTransaction = processedTransaction.copy(amount = largeAmountTransaction.amount)
                    
                    every { transactionProcessorService.processTransaction(largeAmountTransaction) } returns largeAmountProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.amount == BigDecimal("9999999.99") }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
                
                it("should handle small amount transaction") {
                    // Given
                    val smallAmountTransaction = transaction.copy(amount = BigDecimal("0.01"))
                    val message = objectMapper.writeValueAsString(smallAmountTransaction)
                    val smallAmountProcessedTransaction = processedTransaction.copy(amount = smallAmountTransaction.amount)
                    
                    every { transactionProcessorService.processTransaction(smallAmountTransaction) } returns smallAmountProcessedTransaction
                    every { acknowledgment.acknowledge() } returns Unit
                    
                    // When
                    transactionKafkaListener.consumeTransaction(message, topic, partition, offset, acknowledgment)
                    
                    // Then
                    verify { 
                        transactionProcessorService.processTransaction(
                            match { it.amount == BigDecimal("0.01") }
                        ) 
                    }
                    verify { acknowledgment.acknowledge() }
                }
            }
        }
    }
})
