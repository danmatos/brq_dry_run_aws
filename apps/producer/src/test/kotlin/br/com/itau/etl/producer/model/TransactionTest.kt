package br.com.itau.etl.producer.model

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

class TransactionTest : DescribeSpec({

    describe("Transaction") {
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
        }

        describe("data class properties") {
            context("when creating a Transaction with all properties") {
                it("should create transaction correctly") {
                    // Given
                    val id = "txn-123"
                    val accountId = "acc-456"
                    val amount = BigDecimal("1000.50")
                    val type = TransactionType.PIX
                    val description = "Payment to merchant"
                    val timestamp = LocalDateTime.of(2025, 1, 14, 10, 30, 0)
                    val pixData = PixData(
                        pixKey = "user@example.com",
                        pixKeyType = PixKeyType.EMAIL,
                        endToEndId = "E123456789"
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
                        id = "txn-456",
                        accountId = "acc-789",
                        amount = BigDecimal("500.00"),
                        type = TransactionType.TED,
                        description = "TED transfer",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // Then
                    transaction.pixData shouldBe null
                    transaction.type shouldBe TransactionType.TED
                }
            }
        }

        describe("JSON serialization/deserialization") {
            context("when serializing Transaction to JSON") {
                it("should serialize with correct timestamp format") {
                    // Given
                    val transaction = Transaction(
                        id = "txn-789",
                        accountId = "acc-123",
                        amount = BigDecimal("750.25"),
                        type = TransactionType.CREDIT,
                        description = "Credit transaction",
                        timestamp = LocalDateTime.of(2025, 1, 14, 15, 45, 30)
                    )
                    
                    // When
                    val json = objectMapper.writeValueAsString(transaction)
                    
                    // Then
                    json shouldContain "\"timestamp\":\"2025-01-14T15:45:30\""
                    json shouldContain "\"id\":\"txn-789\""
                    json shouldContain "\"amount\":750.25"
                    json shouldContain "\"type\":\"CREDIT\""
                }
            }
            
            context("when deserializing JSON to Transaction") {
                it("should deserialize correctly") {
                    // Given
                    val json = """
                    {
                        "id": "txn-serialization-test",
                        "accountId": "acc-test",
                        "amount": 1250.75,
                        "type": "PIX",
                        "description": "PIX payment",
                        "timestamp": "2025-01-14T09:15:30",
                        "pixData": {
                            "pixKey": "11999887766",
                            "pixKeyType": "PHONE",
                            "endToEndId": "E987654321"
                        }
                    }
                    """.trimIndent()
                    
                    // When
                    val transaction = objectMapper.readValue(json, Transaction::class.java)
                    
                    // Then
                    transaction.id shouldBe "txn-serialization-test"
                    transaction.accountId shouldBe "acc-test"
                    transaction.amount shouldBe BigDecimal("1250.75")
                    transaction.type shouldBe TransactionType.PIX
                    transaction.description shouldBe "PIX payment"
                    transaction.timestamp shouldBe LocalDateTime.of(2025, 1, 14, 9, 15, 30)
                    transaction.pixData shouldNotBe null
                    transaction.pixData!!.pixKey shouldBe "11999887766"
                    transaction.pixData!!.pixKeyType shouldBe PixKeyType.PHONE
                    transaction.pixData!!.endToEndId shouldBe "E987654321"
                }
            }
        }

        describe("equality and hashCode") {
            context("when comparing identical transactions") {
                it("should be equal") {
                    // Given
                    val timestamp = LocalDateTime.now()
                    val pixData = PixData("key", PixKeyType.EMAIL, "end123")
                    
                    val transaction1 = Transaction(
                        id = "same-id",
                        accountId = "same-account",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "Same description",
                        timestamp = timestamp,
                        pixData = pixData
                    )
                    
                    val transaction2 = Transaction(
                        id = "same-id",
                        accountId = "same-account",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "Same description",
                        timestamp = timestamp,
                        pixData = pixData
                    )
                    
                    // Then
                    (transaction1 == transaction2) shouldBe true
                    transaction1.hashCode() shouldBe transaction2.hashCode()
                }
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

    describe("PixData") {
        context("when creating PIX data") {
            it("should create PixData correctly") {
                // Given & When
                val pixData = PixData(
                    pixKey = "12345678901",
                    pixKeyType = PixKeyType.CPF,
                    endToEndId = "E123456789012345678901234567890123"
                )
                
                // Then
                pixData.pixKey shouldBe "12345678901"
                pixData.pixKeyType shouldBe PixKeyType.CPF
                pixData.endToEndId shouldBe "E123456789012345678901234567890123"
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

    describe("ProcessingResult") {
        context("when creating ProcessingResult for valid transaction") {
            it("should create result with no errors") {
                // Given
                val transaction = Transaction(
                    id = "valid-txn",
                    accountId = "acc-valid",
                    amount = BigDecimal("100.00"),
                    type = TransactionType.CREDIT,
                    description = "Valid transaction",
                    timestamp = LocalDateTime.now()
                )
                
                // When
                val result = ProcessingResult(
                    transaction = transaction,
                    isValid = true
                )
                
                // Then
                result.transaction shouldBe transaction
                result.isValid shouldBe true
                result.errors.shouldBeEmpty()
            }
        }
        
        context("when creating ProcessingResult for invalid transaction") {
            it("should create result with errors") {
                // Given
                val transaction = Transaction(
                    id = "invalid-txn",
                    accountId = "acc-invalid",
                    amount = BigDecimal("-100.00"),
                    type = TransactionType.DEBIT,
                    description = "Invalid transaction",
                    timestamp = LocalDateTime.now()
                )
                
                val errors = listOf("Amount cannot be negative", "Account not found")
                
                // When
                val result = ProcessingResult(
                    transaction = transaction,
                    isValid = false,
                    errors = errors
                )
                
                // Then
                result.transaction shouldBe transaction
                result.isValid shouldBe false
                result.errors shouldHaveSize 2
                result.errors shouldContain "Amount cannot be negative"
                result.errors shouldContain "Account not found"
            }
        }
    }
})
