package br.com.itau.etl.consumer.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionModelTest : DescribeSpec({

    describe("Transaction Model Classes") {
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
        }

        describe("Transaction") {
            context("when creating a Transaction with all properties") {
                it("should create transaction correctly") {
                    // Given
                    val id = "consumer-txn-123"
                    val accountId = "consumer-acc-456"
                    val amount = BigDecimal("1500.75")
                    val type = TransactionType.PIX
                    val description = "Consumer payment test"
                    val timestamp = LocalDateTime.of(2025, 1, 14, 16, 30, 0)
                    val pixData = PixData(
                        pixKey = "consumer@example.com",
                        pixKeyType = PixKeyType.EMAIL,
                        endToEndId = "E123456789012345678901234567890123456"
                    )
                    
                    // When
                    val transaction = Transaction(
                        id = id,
                        accountId = accountId,
                        amount = amount,
                        type = type,
                        description = description,
                        timestamp = timestamp,
                        pixData = pixData
                    )
                    
                    // Then
                    transaction.id shouldBe id
                    transaction.accountId shouldBe accountId
                    transaction.amount shouldBe amount
                    transaction.type shouldBe type
                    transaction.description shouldBe description
                    transaction.timestamp shouldBe timestamp
                    transaction.pixData shouldBe pixData
                }
            }
            
            context("when creating a Transaction without PIX data") {
                it("should create transaction with null pixData") {
                    // Given & When
                    val transaction = Transaction(
                        id = "consumer-ted-456",
                        accountId = "consumer-acc-789",
                        amount = BigDecimal("750.00"),
                        type = TransactionType.TED,
                        description = "Consumer TED transfer",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // Then
                    transaction.pixData shouldBe null
                    transaction.type shouldBe TransactionType.TED
                }
            }

            context("when serializing/deserializing Transaction") {
                it("should maintain data integrity") {
                    // Given
                    val originalTransaction = Transaction(
                        id = "serialize-test",
                        accountId = "acc-serialize",
                        amount = BigDecimal("999.99"),
                        type = TransactionType.CREDIT,
                        description = "Serialization test",
                        timestamp = LocalDateTime.of(2025, 1, 14, 12, 0, 0),
                        pixData = PixData(
                            pixKey = "serialize@test.com",
                            pixKeyType = PixKeyType.EMAIL,
                            endToEndId = "E999999999999999999999999999999999999"
                        )
                    )
                    
                    // When
                    val json = objectMapper.writeValueAsString(originalTransaction)
                    val deserializedTransaction = objectMapper.readValue(json, Transaction::class.java)
                    
                    // Then
                    deserializedTransaction shouldBe originalTransaction
                    deserializedTransaction.pixData shouldNotBe null
                    deserializedTransaction.pixData!!.pixKey shouldBe "serialize@test.com"
                }
            }
        }

        describe("ProcessedTransaction") {
            context("when creating ProcessedTransaction") {
                it("should create with all fields including processedAt") {
                    // Given
                    val transactionId = "processed-txn-123"
                    val accountId = "processed-acc-456"
                    val amount = BigDecimal("250.50")
                    val type = TransactionType.DEBIT
                    val description = "Processed transaction test"
                    val timestamp = LocalDateTime.of(2025, 1, 14, 10, 0, 0)
                    val processedAt = LocalDateTime.of(2025, 1, 14, 10, 1, 0)
                    val status = TransactionStatus.PROCESSED
                    
                    // When
                    val processedTransaction = ProcessedTransaction(
                        transactionId = transactionId,
                        accountId = accountId,
                        amount = amount,
                        type = type,
                        description = description,
                        timestamp = timestamp,
                        processedAt = processedAt,
                        status = status
                    )
                    
                    // Then
                    processedTransaction.transactionId shouldBe transactionId
                    processedTransaction.accountId shouldBe accountId
                    processedTransaction.amount shouldBe amount
                    processedTransaction.type shouldBe type
                    processedTransaction.description shouldBe description
                    processedTransaction.timestamp shouldBe timestamp
                    processedTransaction.processedAt shouldBe processedAt
                    processedTransaction.status shouldBe status
                    processedTransaction.pixData shouldBe null
                }
            }
            
            context("when creating ProcessedTransaction with PIX data") {
                it("should include PIX information") {
                    // Given
                    val pixData = PixData(
                        pixKey = "processed@pix.com",
                        pixKeyType = PixKeyType.EMAIL,
                        endToEndId = "E555555555555555555555555555555555555"
                    )
                    
                    // When
                    val processedTransaction = ProcessedTransaction(
                        transactionId = "pix-processed-123",
                        accountId = "pix-acc-456",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "PIX processed transaction",
                        timestamp = LocalDateTime.now(),
                        processedAt = LocalDateTime.now(),
                        pixData = pixData,
                        status = TransactionStatus.PROCESSED
                    )
                    
                    // Then
                    processedTransaction.pixData shouldBe pixData
                    processedTransaction.type shouldBe TransactionType.PIX
                    processedTransaction.status shouldBe TransactionStatus.PROCESSED
                }
            }
            
            context("when creating ProcessedTransaction with different statuses") {
                it("should handle ERROR status") {
                    // Given & When
                    val errorTransaction = ProcessedTransaction(
                        transactionId = "error-txn",
                        accountId = "error-acc",
                        amount = BigDecimal("50.00"),
                        type = TransactionType.CREDIT,
                        description = "Error transaction",
                        timestamp = LocalDateTime.now(),
                        processedAt = LocalDateTime.now(),
                        status = TransactionStatus.ERROR
                    )
                    
                    // Then
                    errorTransaction.status shouldBe TransactionStatus.ERROR
                }
                
                it("should handle REJECTED status") {
                    // Given & When
                    val rejectedTransaction = ProcessedTransaction(
                        transactionId = "rejected-txn",
                        accountId = "rejected-acc",
                        amount = BigDecimal("75.00"),
                        type = TransactionType.DEBIT,
                        description = "Rejected transaction",
                        timestamp = LocalDateTime.now(),
                        processedAt = LocalDateTime.now(),
                        status = TransactionStatus.REJECTED
                    )
                    
                    // Then
                    rejectedTransaction.status shouldBe TransactionStatus.REJECTED
                }
            }
        }

        describe("TransactionType enum") {
            context("when using all transaction types") {
                it("should have all expected values") {
                    // Given & When & Then
                    TransactionType.PIX shouldNotBe null
                    TransactionType.TED shouldNotBe null
                    TransactionType.DOC shouldNotBe null
                    TransactionType.CREDIT shouldNotBe null
                    TransactionType.DEBIT shouldNotBe null
                    
                    TransactionType.values().size shouldBe 5
                }
            }
        }

        describe("TransactionStatus enum") {
            context("when using all transaction statuses") {
                it("should have all expected values") {
                    // Given & When & Then
                    TransactionStatus.PROCESSED shouldNotBe null
                    TransactionStatus.REJECTED shouldNotBe null
                    TransactionStatus.ERROR shouldNotBe null
                    
                    TransactionStatus.values().size shouldBe 3
                }
            }
        }

        describe("PixData") {
            context("when creating PIX data with different key types") {
                it("should create with CPF key type") {
                    // Given & When
                    val pixData = PixData(
                        pixKey = "12345678901",
                        pixKeyType = PixKeyType.CPF,
                        endToEndId = "E111111111111111111111111111111111111"
                    )
                    
                    // Then
                    pixData.pixKey shouldBe "12345678901"
                    pixData.pixKeyType shouldBe PixKeyType.CPF
                    pixData.endToEndId shouldBe "E111111111111111111111111111111111111"
                }
                
                it("should create with CNPJ key type") {
                    // Given & When
                    val pixData = PixData(
                        pixKey = "12345678000199",
                        pixKeyType = PixKeyType.CNPJ,
                        endToEndId = "E222222222222222222222222222222222222"
                    )
                    
                    // Then
                    pixData.pixKeyType shouldBe PixKeyType.CNPJ
                }
                
                it("should create with PHONE key type") {
                    // Given & When
                    val pixData = PixData(
                        pixKey = "11987654321",
                        pixKeyType = PixKeyType.PHONE,
                        endToEndId = "E333333333333333333333333333333333333"
                    )
                    
                    // Then
                    pixData.pixKeyType shouldBe PixKeyType.PHONE
                }
                
                it("should create with RANDOM key type") {
                    // Given & When
                    val pixData = PixData(
                        pixKey = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                        pixKeyType = PixKeyType.RANDOM,
                        endToEndId = "E444444444444444444444444444444444444"
                    )
                    
                    // Then
                    pixData.pixKeyType shouldBe PixKeyType.RANDOM
                }
            }
        }

        describe("PixKeyType enum") {
            context("when using all PIX key types") {
                it("should have all expected values") {
                    // Given & When & Then
                    PixKeyType.CPF shouldNotBe null
                    PixKeyType.CNPJ shouldNotBe null
                    PixKeyType.EMAIL shouldNotBe null
                    PixKeyType.PHONE shouldNotBe null
                    PixKeyType.RANDOM shouldNotBe null
                    
                    PixKeyType.values().size shouldBe 5
                }
            }
        }
    }
})
