package br.com.itau.etl.consumer.service

import br.com.itau.etl.consumer.model.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.math.BigDecimal
import java.time.LocalDateTime

class DynamoDbServiceTest : DescribeSpec({

    describe("DynamoDbService") {
        val dynamoDbClient = mockk<DynamoDbClient>()
        val dynamoDbService = DynamoDbService()
        
        beforeEach {
            ReflectionTestUtils.setField(dynamoDbService, "dynamoDbClient", dynamoDbClient)
            ReflectionTestUtils.setField(dynamoDbService, "transactionsTable", "test-transactions-table")
            clearAllMocks()
        }

        describe("saveTransaction") {
            val processedTransaction = ProcessedTransaction(
                transactionId = "txn-12345",
                accountId = "acc-67890",
                amount = BigDecimal("150.75"),
                type = TransactionType.PIX,
                description = "PIX payment test",
                timestamp = LocalDateTime.of(2025, 1, 14, 10, 30, 0),
                processedAt = LocalDateTime.of(2025, 1, 14, 10, 31, 0),
                pixData = PixData(
                    pixKey = "test@example.com",
                    pixKeyType = PixKeyType.EMAIL,
                    endToEndId = "E12345678901234567890123456789012345"
                ),
                status = TransactionStatus.PROCESSED
            )

            context("when saving transaction with PIX data") {
                it("should save transaction to DynamoDB") {
                    // Given
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.saveTransaction(processedTransaction)
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.tableName() == "test-transactions-table" &&
                                request.item()["transaction_id"]?.s() == "txn-12345" &&
                                request.item()["account_id"]?.s() == "acc-67890" &&
                                request.item()["amount"]?.n() == "150.75" &&
                                request.item()["type"]?.s() == "PIX" &&
                                request.item()["description"]?.s() == "PIX payment test" &&
                                request.item()["status"]?.s() == "PROCESSED" &&
                                request.item()["pix_key"]?.s() == "test@example.com" &&
                                request.item()["pix_key_type"]?.s() == "EMAIL" &&
                                request.item()["end_to_end_id"]?.s() == "E12345678901234567890123456789012345"
                            }
                        )
                    }
                }
            }
            
            context("when saving transaction without PIX data") {
                it("should save transaction without PIX fields") {
                    // Given
                    val transactionWithoutPix = processedTransaction.copy(pixData = null, type = TransactionType.TED)
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.saveTransaction(transactionWithoutPix)
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.tableName() == "test-transactions-table" &&
                                request.item()["transaction_id"]?.s() == "txn-12345" &&
                                request.item()["type"]?.s() == "TED" &&
                                !request.item().containsKey("pix_key") &&
                                !request.item().containsKey("pix_key_type") &&
                                !request.item().containsKey("end_to_end_id")
                            }
                        )
                    }
                }
            }
            
            context("when saving transaction with ERROR status") {
                it("should save transaction with ERROR status") {
                    // Given
                    val errorTransaction = processedTransaction.copy(status = TransactionStatus.ERROR)
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.saveTransaction(errorTransaction)
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.item()["status"]?.s() == "ERROR"
                            }
                        )
                    }
                }
            }
            
            context("when DynamoDB put operation fails") {
                it("should throw exception") {
                    // Given
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } throws RuntimeException("DynamoDB put error")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        dynamoDbService.saveTransaction(processedTransaction)
                    }
                    
                    verify { dynamoDbClient.putItem(any<PutItemRequest>()) }
                }
            }
        }

        describe("transactionExists") {
            val transactionId = "txn-exists-test"
            
            context("when transaction exists in DynamoDB") {
                it("should return true") {
                    // Given
                    val response = mockk<GetItemResponse> {
                        every { hasItem() } returns true
                    }
                    every { dynamoDbClient.getItem(any<GetItemRequest>()) } returns response
                    
                    // When
                    val result = dynamoDbService.transactionExists(transactionId)
                    
                    // Then
                    result shouldBe true
                    
                    verify {
                        dynamoDbClient.getItem(
                            match<GetItemRequest> { 
                                it.tableName() == "test-transactions-table" &&
                                it.key()["transaction_id"]?.s() == transactionId
                            }
                        )
                    }
                }
            }
            
            context("when transaction does not exist in DynamoDB") {
                it("should return false") {
                    // Given
                    val response = mockk<GetItemResponse> {
                        every { hasItem() } returns false
                    }
                    every { dynamoDbClient.getItem(any<GetItemRequest>()) } returns response
                    
                    // When
                    val result = dynamoDbService.transactionExists(transactionId)
                    
                    // Then
                    result shouldBe false
                }
            }
            
            context("when DynamoDB get operation fails") {
                it("should return false and log error") {
                    // Given
                    every { dynamoDbClient.getItem(any<GetItemRequest>()) } throws RuntimeException("DynamoDB get error")
                    
                    // When
                    val result = dynamoDbService.transactionExists(transactionId)
                    
                    // Then
                    result shouldBe false
                    
                    verify { dynamoDbClient.getItem(any<GetItemRequest>()) }
                }
            }
        }

        describe("updateTransactionStatus") {
            val transactionId = "txn-update-test"
            val newStatus = TransactionStatus.PROCESSED
            
            context("when updating transaction status successfully") {
                it("should update status in DynamoDB") {
                    // Given
                    val updateResponse = mockk<UpdateItemResponse>()
                    every { dynamoDbClient.updateItem(any<UpdateItemRequest>()) } returns updateResponse
                    
                    // When
                    dynamoDbService.updateTransactionStatus(transactionId, newStatus)
                    
                    // Then
                    verify {
                        dynamoDbClient.updateItem(
                            match<UpdateItemRequest> { request ->
                                request.tableName() == "test-transactions-table" &&
                                request.key()["transaction_id"]?.s() == transactionId &&
                                request.updateExpression() == "SET #status = :status, updated_at = :updated_at" &&
                                request.expressionAttributeNames()["#status"] == "status" &&
                                request.expressionAttributeValues()[":status"]?.s() == "PROCESSED" &&
                                request.expressionAttributeValues().containsKey(":updated_at")
                            }
                        )
                    }
                }
            }
            
            context("when updating to ERROR status") {
                it("should update status to ERROR") {
                    // Given
                    val updateResponse = mockk<UpdateItemResponse>()
                    every { dynamoDbClient.updateItem(any<UpdateItemRequest>()) } returns updateResponse
                    
                    // When
                    dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.ERROR)
                    
                    // Then
                    verify {
                        dynamoDbClient.updateItem(
                            match<UpdateItemRequest> { request ->
                                request.expressionAttributeValues()[":status"]?.s() == "ERROR"
                            }
                        )
                    }
                }
            }
            
            context("when updating to REJECTED status") {
                it("should update status to REJECTED") {
                    // Given
                    val updateResponse = mockk<UpdateItemResponse>()
                    every { dynamoDbClient.updateItem(any<UpdateItemRequest>()) } returns updateResponse
                    
                    // When
                    dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.REJECTED)
                    
                    // Then
                    verify {
                        dynamoDbClient.updateItem(
                            match<UpdateItemRequest> { request ->
                                request.expressionAttributeValues()[":status"]?.s() == "REJECTED"
                            }
                        )
                    }
                }
            }
            
            context("when DynamoDB update operation fails") {
                it("should throw exception") {
                    // Given
                    every { dynamoDbClient.updateItem(any<UpdateItemRequest>()) } throws RuntimeException("DynamoDB update error")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        dynamoDbService.updateTransactionStatus(transactionId, newStatus)
                    }
                    
                    verify { dynamoDbClient.updateItem(any<UpdateItemRequest>()) }
                }
            }
        }
    }
})
