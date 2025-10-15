package br.com.itau.etl.producer.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoDbServiceTest : DescribeSpec({

    describe("DynamoDbService") {
        val dynamoDbClient = mockk<DynamoDbClient>()
        val dynamoDbService = DynamoDbService()
        
        beforeEach {
            ReflectionTestUtils.setField(dynamoDbService, "dynamoDbClient", dynamoDbClient)
            ReflectionTestUtils.setField(dynamoDbService, "processedFilesTable", "test-processed-files-table")
            clearAllMocks()
        }

        describe("isFileProcessed") {
            val fileKey = "transactions/2025/10/14/test-file.json"
            
            context("when file exists in DynamoDB") {
                it("should return true") {
                    // Given
                    val response = mockk<GetItemResponse> {
                        every { hasItem() } returns true
                    }
                    every { dynamoDbClient.getItem(any<GetItemRequest>()) } returns response
                    
                    // When
                    val result = dynamoDbService.isFileProcessed(fileKey)
                    
                    // Then
                    result shouldBe true
                    
                    verify {
                        dynamoDbClient.getItem(
                            match<GetItemRequest> { 
                                it.tableName() == "test-processed-files-table" &&
                                it.key()["file_key"]?.s() == fileKey
                            }
                        )
                    }
                }
            }
            
            context("when file does not exist in DynamoDB") {
                it("should return false") {
                    // Given
                    val response = mockk<GetItemResponse> {
                        every { hasItem() } returns false
                    }
                    every { dynamoDbClient.getItem(any<GetItemRequest>()) } returns response
                    
                    // When
                    val result = dynamoDbService.isFileProcessed(fileKey)
                    
                    // Then
                    result shouldBe false
                }
            }
            
            context("when DynamoDB operation fails") {
                it("should return false and log error") {
                    // Given
                    every { dynamoDbClient.getItem(any<GetItemRequest>()) } throws RuntimeException("DynamoDB error")
                    
                    // When
                    val result = dynamoDbService.isFileProcessed(fileKey)
                    
                    // Then
                    result shouldBe false
                }
            }
        }

        describe("markFileAsProcessed") {
            val fileKey = "transactions/2025/10/14/test-file.json"
            
            context("when marking file as processed with default status") {
                it("should save item to DynamoDB with PROCESSED status") {
                    // Given
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.markFileAsProcessed(fileKey)
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.tableName() == "test-processed-files-table" &&
                                request.item()["file_key"]?.s() == fileKey &&
                                request.item()["status"]?.s() == "PROCESSED" &&
                                request.item()["processed_at"]?.s() != null
                            }
                        )
                    }
                }
            }
            
            context("when marking file as processed with custom status") {
                it("should save item to DynamoDB with custom status") {
                    // Given
                    val customStatus = "REJECTED"
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.markFileAsProcessed(fileKey, customStatus)
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.tableName() == "test-processed-files-table" &&
                                request.item()["file_key"]?.s() == fileKey &&
                                request.item()["status"]?.s() == customStatus &&
                                request.item()["processed_at"]?.s() != null
                            }
                        )
                    }
                }
            }
            
            context("when marking file as ERROR") {
                it("should save item to DynamoDB with ERROR status") {
                    // Given
                    val errorStatus = "ERROR"
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.markFileAsProcessed(fileKey, errorStatus)
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.tableName() == "test-processed-files-table" &&
                                request.item()["file_key"]?.s() == fileKey &&
                                request.item()["status"]?.s() == errorStatus &&
                                request.item()["processed_at"]?.s() != null
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
                        dynamoDbService.markFileAsProcessed(fileKey)
                    }
                    
                    verify {
                        dynamoDbClient.putItem(any<PutItemRequest>())
                    }
                }
            }
            
            context("when handling different file key formats") {
                it("should handle file keys with special characters") {
                    // Given
                    val specialFileKey = "transactions/2025/10/14/file-with-special-chars_123.json"
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.markFileAsProcessed(specialFileKey, "PROCESSED")
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.item()["file_key"]?.s() == specialFileKey
                            }
                        )
                    }
                }
                
                it("should handle very long file keys") {
                    // Given
                    val longFileKey = "transactions/" + "very-long-path/".repeat(10) + "long-filename.json"
                    val putResponse = mockk<PutItemResponse>()
                    every { dynamoDbClient.putItem(any<PutItemRequest>()) } returns putResponse
                    
                    // When
                    dynamoDbService.markFileAsProcessed(longFileKey, "PROCESSED")
                    
                    // Then
                    verify {
                        dynamoDbClient.putItem(
                            match<PutItemRequest> { request ->
                                request.item()["file_key"]?.s() == longFileKey
                            }
                        )
                    }
                }
            }
        }
    }
})
