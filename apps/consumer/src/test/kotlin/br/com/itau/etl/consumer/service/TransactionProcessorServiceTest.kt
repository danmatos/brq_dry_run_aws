package br.com.itau.etl.consumer.service

import br.com.itau.etl.consumer.model.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionProcessorServiceTest : DescribeSpec({

    describe("TransactionProcessorService") {
        val dynamoDbService = mockk<DynamoDbService>()
        val transactionProcessorService = TransactionProcessorService()
        
        beforeEach {
            ReflectionTestUtils.setField(transactionProcessorService, "dynamoDbService", dynamoDbService)
            clearAllMocks()
        }

        describe("processTransaction") {
            val transaction = Transaction(
                id = "txn-12345",
                accountId = "acc-67890",
                amount = BigDecimal("150.75"),
                type = TransactionType.PIX,
                description = "PIX payment test",
                timestamp = LocalDateTime.of(2025, 1, 14, 10, 30, 0),
                pixData = PixData(
                    pixKey = "test@example.com",
                    pixKeyType = PixKeyType.EMAIL,
                    endToEndId = "E12345678901234567890123456789012345"
                )
            )

            context("when transaction is new and processing succeeds") {
                it("should process transaction successfully") {
                    // Given
                    every { dynamoDbService.transactionExists(transaction.id) } returns false
                    every { dynamoDbService.saveTransaction(any()) } returns Unit
                    
                    // When
                    val result = transactionProcessorService.processTransaction(transaction)
                    
                    // Then
                    result shouldNotBe null
                    result.transactionId shouldBe transaction.id
                    result.accountId shouldBe transaction.accountId
                    result.amount shouldBe transaction.amount
                    result.type shouldBe transaction.type
                    result.description shouldBe transaction.description
                    result.timestamp shouldBe transaction.timestamp
                    result.pixData shouldBe transaction.pixData
                    result.status shouldBe TransactionStatus.PROCESSED
                    result.processedAt shouldNotBe null
                    
                    verify { dynamoDbService.transactionExists(transaction.id) }
                    verify { dynamoDbService.saveTransaction(any()) }
                }
            }
            
            context("when transaction already exists (idempotency)") {
                it("should throw IllegalStateException") {
                    // Given
                    every { dynamoDbService.transactionExists(transaction.id) } returns true
                    
                    // When & Then
                    val exception = shouldThrow<IllegalStateException> {
                        transactionProcessorService.processTransaction(transaction)
                    }
                    
                    exception.message shouldBe "Transaction already processed"
                    
                    verify { dynamoDbService.transactionExists(transaction.id) }
                    verify(exactly = 0) { dynamoDbService.saveTransaction(any()) }
                }
            }
            
            context("when DynamoDB save operation fails") {
                it("should save error transaction and throw exception") {
                    // Given
                    every { dynamoDbService.transactionExists(transaction.id) } returns false
                    every { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.PROCESSED }) } throws RuntimeException("DynamoDB error")
                    every { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.ERROR }) } returns Unit
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        transactionProcessorService.processTransaction(transaction)
                    }
                    
                    verify { dynamoDbService.transactionExists(transaction.id) }
                    verify { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.PROCESSED }) }
                    verify { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.ERROR }) }
                }
            }
            
            context("when both save operations fail") {
                it("should throw exception after attempting to save error status") {
                    // Given
                    every { dynamoDbService.transactionExists(transaction.id) } returns false
                    every { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.PROCESSED }) } throws RuntimeException("DynamoDB error")
                    every { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.ERROR }) } throws RuntimeException("DynamoDB save error")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        transactionProcessorService.processTransaction(transaction)
                    }
                    
                    verify { dynamoDbService.transactionExists(transaction.id) }
                    verify { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.PROCESSED }) }
                    verify { dynamoDbService.saveTransaction(match { it.status == TransactionStatus.ERROR }) }
                }
            }
            
            context("when processing transaction without PIX data") {
                it("should process transaction successfully") {
                    // Given
                    val simpleTransaction = transaction.copy(pixData = null, type = TransactionType.TED)
                    every { dynamoDbService.transactionExists(simpleTransaction.id) } returns false
                    every { dynamoDbService.saveTransaction(any()) } returns Unit
                    
                    // When
                    val result = transactionProcessorService.processTransaction(simpleTransaction)
                    
                    // Then
                    result.pixData shouldBe null
                    result.type shouldBe TransactionType.TED
                    result.status shouldBe TransactionStatus.PROCESSED
                    
                    verify { dynamoDbService.saveTransaction(match { it.pixData == null }) }
                }
            }
            
            context("when processing different transaction types") {
                it("should handle CREDIT transaction") {
                    // Given
                    val creditTransaction = transaction.copy(type = TransactionType.CREDIT, pixData = null)
                    every { dynamoDbService.transactionExists(creditTransaction.id) } returns false
                    every { dynamoDbService.saveTransaction(any()) } returns Unit
                    
                    // When
                    val result = transactionProcessorService.processTransaction(creditTransaction)
                    
                    // Then
                    result.type shouldBe TransactionType.CREDIT
                    result.status shouldBe TransactionStatus.PROCESSED
                }
                
                it("should handle DEBIT transaction") {
                    // Given
                    val debitTransaction = transaction.copy(type = TransactionType.DEBIT, pixData = null)
                    every { dynamoDbService.transactionExists(debitTransaction.id) } returns false
                    every { dynamoDbService.saveTransaction(any()) } returns Unit
                    
                    // When
                    val result = transactionProcessorService.processTransaction(debitTransaction)
                    
                    // Then
                    result.type shouldBe TransactionType.DEBIT
                    result.status shouldBe TransactionStatus.PROCESSED
                }
            }
        }

        describe("reprocessFailedTransaction") {
            val transactionId = "failed-txn-123"
            
            context("when reprocessing succeeds") {
                it("should update transaction status to PROCESSED") {
                    // Given
                    every { dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.PROCESSED) } returns Unit
                    
                    // When
                    transactionProcessorService.reprocessFailedTransaction(transactionId)
                    
                    // Then
                    verify { dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.PROCESSED) }
                }
            }
            
            context("when reprocessing fails") {
                it("should throw exception") {
                    // Given
                    every { dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.PROCESSED) } throws RuntimeException("Update failed")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        transactionProcessorService.reprocessFailedTransaction(transactionId)
                    }
                    
                    verify { dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.PROCESSED) }
                }
            }
            
            context("when reprocessing multiple transactions") {
                it("should handle batch reprocessing") {
                    // Given
                    val transactionIds = listOf("txn-1", "txn-2", "txn-3")
                    every { dynamoDbService.updateTransactionStatus(any(), TransactionStatus.PROCESSED) } returns Unit
                    
                    // When
                    transactionIds.forEach { id ->
                        transactionProcessorService.reprocessFailedTransaction(id)
                    }
                    
                    // Then
                    transactionIds.forEach { id ->
                        verify { dynamoDbService.updateTransactionStatus(id, TransactionStatus.PROCESSED) }
                    }
                }
            }
        }
    }
})
