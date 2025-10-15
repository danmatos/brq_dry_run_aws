package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.PixData
import br.com.itau.etl.producer.model.PixKeyType
import br.com.itau.etl.producer.model.Transaction
import br.com.itau.etl.producer.model.TransactionType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

class ValidationServiceTest : DescribeSpec({

    describe("ValidationService") {
        val configurationService = mockk<ConfigurationService>()
        val validationService = ValidationService()
        
        beforeEach {
            ReflectionTestUtils.setField(validationService, "configurationService", configurationService)
        }

        describe("validate") {
            
            context("when transaction is valid") {
                it("should return valid result for CREDIT transaction") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-001",
                        accountId = "ACC-001",
                        amount = BigDecimal("100.50"),
                        type = TransactionType.CREDIT,
                        description = "Valid transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe true
                    result.errors.shouldBeEmpty()
                    result.transaction shouldBe transaction
                }
                
                it("should return valid result for PIX transaction when PIX is enabled") {
                    // Given
                    every { configurationService.isPixEnabled() } returns true
                    
                    val pixData = PixData(
                        pixKey = "11111111111",
                        pixKeyType = PixKeyType.CPF,
                        endToEndId = "E2E123456789"
                    )
                    
                    val transaction = Transaction(
                        id = "TXN-002",
                        accountId = "ACC-002",
                        amount = BigDecimal("250.75"),
                        type = TransactionType.PIX,
                        description = "PIX transaction",
                        timestamp = LocalDateTime.now(),
                        pixData = pixData
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe true
                    result.errors.shouldBeEmpty()
                }
            }
            
            context("when transaction has invalid amount") {
                it("should return invalid result for zero amount") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-003",
                        accountId = "ACC-003",
                        amount = BigDecimal.ZERO,
                        type = TransactionType.DEBIT,
                        description = "Zero amount transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "Amount must be greater than zero"
                }
                
                it("should return invalid result for negative amount") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-004",
                        accountId = "ACC-004",
                        amount = BigDecimal("-10.50"),
                        type = TransactionType.DEBIT,
                        description = "Negative amount transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "Amount must be greater than zero"
                }
                
                it("should return invalid result for amount exceeding limit") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-005",
                        accountId = "ACC-005",
                        amount = BigDecimal("150000.00"),
                        type = TransactionType.CREDIT,
                        description = "High amount transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "Amount exceeds maximum limit"
                }
            }
            
            context("when account ID is invalid") {
                it("should return invalid result for blank account ID") {
                    // Given
                    val transaction = Transaction(
                        id = "TXN-006",
                        accountId = "",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.CREDIT,
                        description = "Blank account ID transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "Account ID cannot be blank"
                }
            }
            
            context("when PIX transaction has issues") {
                it("should return invalid result when PIX is disabled") {
                    // Given
                    every { configurationService.isPixEnabled() } returns false
                    
                    val transaction = Transaction(
                        id = "TXN-007",
                        accountId = "ACC-007",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "PIX transaction when disabled",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "PIX transactions are currently disabled"
                }
                
                it("should return invalid result when PIX data is missing") {
                    // Given
                    every { configurationService.isPixEnabled() } returns true
                    
                    val transaction = Transaction(
                        id = "TXN-008",
                        accountId = "ACC-008",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "PIX transaction without data",
                        timestamp = LocalDateTime.now(),
                        pixData = null
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "PIX data is required for PIX transactions"
                }
                
                it("should return invalid result when PIX key is blank") {
                    // Given
                    every { configurationService.isPixEnabled() } returns true
                    
                    val pixData = PixData(
                        pixKey = "",
                        pixKeyType = PixKeyType.CPF,
                        endToEndId = "E2E123456789"
                    )
                    
                    val transaction = Transaction(
                        id = "TXN-009",
                        accountId = "ACC-009",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "PIX transaction with blank key",
                        timestamp = LocalDateTime.now(),
                        pixData = pixData
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "PIX key cannot be blank"
                }
                
                it("should return invalid result when end-to-end ID is blank") {
                    // Given
                    every { configurationService.isPixEnabled() } returns true
                    
                    val pixData = PixData(
                        pixKey = "11111111111",
                        pixKeyType = PixKeyType.CPF,
                        endToEndId = ""
                    )
                    
                    val transaction = Transaction(
                        id = "TXN-010",
                        accountId = "ACC-010",
                        amount = BigDecimal("100.00"),
                        type = TransactionType.PIX,
                        description = "PIX transaction with blank end-to-end ID",
                        timestamp = LocalDateTime.now(),
                        pixData = pixData
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldContain "End-to-end ID cannot be blank"
                }
            }
            
            context("when transaction has multiple validation errors") {
                it("should return all validation errors") {
                    // Given
                    every { configurationService.isPixEnabled() } returns false
                    
                    val transaction = Transaction(
                        id = "TXN-011",
                        accountId = "",
                        amount = BigDecimal("-50.00"),
                        type = TransactionType.PIX,
                        description = "Invalid transaction with multiple errors",
                        timestamp = LocalDateTime.now()
                    )
                    
                    // When
                    val result = validationService.validate(transaction)
                    
                    // Then
                    result.isValid shouldBe false
                    result.errors shouldHaveSize 3
                    result.errors shouldContain "Amount must be greater than zero"
                    result.errors shouldContain "Account ID cannot be blank"
                    result.errors shouldContain "PIX transactions are currently disabled"
                }
            }
        }
    }
})
