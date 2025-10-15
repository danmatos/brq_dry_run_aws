package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.Transaction
import br.com.itau.etl.producer.model.TransactionType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDateTime

class S3ServiceTest : DescribeSpec({

    describe("S3Service") {
        val s3Client = mockk<S3Client>()
        val objectMapper = mockk<ObjectMapper>()
        val s3Service = S3Service()
        
        beforeEach {
            ReflectionTestUtils.setField(s3Service, "s3Client", s3Client)
            ReflectionTestUtils.setField(s3Service, "objectMapper", objectMapper)
            ReflectionTestUtils.setField(s3Service, "inputBucket", "test-input-bucket")
            ReflectionTestUtils.setField(s3Service, "rejectedBucket", "test-rejected-bucket")
            clearAllMocks()
        }

        describe("listPendingFiles") {
            
            context("when S3 has files") {
                it("should return list of file keys") {
                    // Given
                    val s3Object1 = mockk<S3Object> {
                        every { key() } returns "transactions/2025/10/14/file1.json"
                    }
                    val s3Object2 = mockk<S3Object> {
                        every { key() } returns "transactions/2025/10/14/file2.json"
                    }
                    
                    val listResponse = mockk<ListObjectsV2Response> {
                        every { contents() } returns listOf(s3Object1, s3Object2)
                    }
                    
                    every { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse
                    
                    // When
                    val result = s3Service.listPendingFiles()
                    
                    // Then
                    result shouldHaveSize 2
                    result shouldBe listOf(
                        "transactions/2025/10/14/file1.json",
                        "transactions/2025/10/14/file2.json"
                    )
                    
                    verify {
                        s3Client.listObjectsV2(
                            match<ListObjectsV2Request> { 
                                it.bucket() == "test-input-bucket" && 
                                it.prefix() == "transactions/"
                            }
                        )
                    }
                }
            }
            
            context("when S3 has no files") {
                it("should return empty list") {
                    // Given
                    val listResponse = mockk<ListObjectsV2Response> {
                        every { contents() } returns emptyList()
                    }
                    
                    every { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse
                    
                    // When
                    val result = s3Service.listPendingFiles()
                    
                    // Then
                    result.shouldBeEmpty()
                }
            }
            
            context("when S3 operation fails") {
                it("should return empty list and log error") {
                    // Given
                    every { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } throws RuntimeException("S3 error")
                    
                    // When
                    val result = s3Service.listPendingFiles()
                    
                    // Then
                    result.shouldBeEmpty()
                }
            }
        }

        describe("readTransactionsFromFile") {
            
            context("when file contains valid JSON Lines") {
                it("should return list of transactions") {
                    // Given
                    val fileKey = "transactions/2025/10/14/test.json"
                    val jsonContent = """
                        {"id":"TXN-001","accountId":"ACC-001","amount":100.50,"type":"CREDIT","description":"Test 1","timestamp":"2025-10-14T10:30:00"}
                        {"id":"TXN-002","accountId":"ACC-002","amount":250.75,"type":"DEBIT","description":"Test 2","timestamp":"2025-10-14T10:31:00"}
                    """.trimIndent()
                    
                    val mockResponse = mockk<ResponseInputStream<GetObjectResponse>> {
                        every { readAllBytes() } returns jsonContent.toByteArray()
                    }
                    
                    every { s3Client.getObject(any<GetObjectRequest>()) } returns mockResponse
                    
                    val transaction1 = Transaction(
                        id = "TXN-001",
                        accountId = "ACC-001", 
                        amount = BigDecimal("100.50"),
                        type = TransactionType.CREDIT,
                        description = "Test 1",
                        timestamp = LocalDateTime.of(2025, 10, 14, 10, 30, 0)
                    )
                    
                    val transaction2 = Transaction(
                        id = "TXN-002",
                        accountId = "ACC-002",
                        amount = BigDecimal("250.75"), 
                        type = TransactionType.DEBIT,
                        description = "Test 2",
                        timestamp = LocalDateTime.of(2025, 10, 14, 10, 31, 0)
                    )
                    
                    every { 
                        objectMapper.readValue<Transaction>(
                            """{"id":"TXN-001","accountId":"ACC-001","amount":100.50,"type":"CREDIT","description":"Test 1","timestamp":"2025-10-14T10:30:00"}"""
                        )
                    } returns transaction1
                    
                    every { 
                        objectMapper.readValue<Transaction>(
                            """{"id":"TXN-002","accountId":"ACC-002","amount":250.75,"type":"DEBIT","description":"Test 2","timestamp":"2025-10-14T10:31:00"}"""
                        )
                    } returns transaction2
                    
                    // When
                    val result = s3Service.readTransactionsFromFile(fileKey)
                    
                    // Then
                    result shouldHaveSize 2
                    result[0] shouldBe transaction1
                    result[1] shouldBe transaction2
                    
                    verify {
                        s3Client.getObject(
                            match<GetObjectRequest> { 
                                it.bucket() == "test-input-bucket" && 
                                it.key() == fileKey
                            }
                        )
                    }
                }
            }
            
            context("when file is empty") {
                it("should return empty list") {
                    // Given
                    val fileKey = "transactions/2025/10/14/empty.json"
                    val jsonContent = ""
                    
                    val mockResponse = mockk<ResponseInputStream<GetObjectResponse>> {
                        every { readAllBytes() } returns jsonContent.toByteArray()
                    }
                    
                    every { s3Client.getObject(any<GetObjectRequest>()) } returns mockResponse
                    
                    // When
                    val result = s3Service.readTransactionsFromFile(fileKey)
                    
                    // Then
                    result.shouldBeEmpty()
                }
            }
            
            context("when file contains blank lines") {
                it("should ignore blank lines") {
                    // Given
                    val fileKey = "transactions/2025/10/14/with-blanks.json"
                    val jsonContent = """
                        {"id":"TXN-001","accountId":"ACC-001","amount":100.50,"type":"CREDIT","description":"Test 1","timestamp":"2025-10-14T10:30:00"}
                        
                        {"id":"TXN-002","accountId":"ACC-002","amount":250.75,"type":"DEBIT","description":"Test 2","timestamp":"2025-10-14T10:31:00"}
                        
                    """.trimIndent()
                    
                    val mockResponse = mockk<ResponseInputStream<GetObjectResponse>> {
                        every { readAllBytes() } returns jsonContent.toByteArray()
                    }
                    
                    every { s3Client.getObject(any<GetObjectRequest>()) } returns mockResponse
                    
                    val transaction1 = Transaction(
                        id = "TXN-001",
                        accountId = "ACC-001",
                        amount = BigDecimal("100.50"),
                        type = TransactionType.CREDIT,
                        description = "Test 1",
                        timestamp = LocalDateTime.of(2025, 10, 14, 10, 30, 0)
                    )
                    
                    val transaction2 = Transaction(
                        id = "TXN-002",
                        accountId = "ACC-002",
                        amount = BigDecimal("250.75"),
                        type = TransactionType.DEBIT,
                        description = "Test 2",
                        timestamp = LocalDateTime.of(2025, 10, 14, 10, 31, 0)
                    )
                    
                    every { 
                        objectMapper.readValue<Transaction>(
                            """{"id":"TXN-001","accountId":"ACC-001","amount":100.50,"type":"CREDIT","description":"Test 1","timestamp":"2025-10-14T10:30:00"}"""
                        )
                    } returns transaction1
                    
                    every { 
                        objectMapper.readValue<Transaction>(
                            """{"id":"TXN-002","accountId":"ACC-002","amount":250.75,"type":"DEBIT","description":"Test 2","timestamp":"2025-10-14T10:31:00"}"""
                        )
                    } returns transaction2
                    
                    // When
                    val result = s3Service.readTransactionsFromFile(fileKey)
                    
                    // Then
                    result shouldHaveSize 2
                }
            }
            
            context("when S3 operation fails") {
                it("should return empty list and log error") {
                    // Given
                    val fileKey = "transactions/2025/10/14/error.json"
                    every { s3Client.getObject(any<GetObjectRequest>()) } throws RuntimeException("S3 error")
                    
                    // When
                    val result = s3Service.readTransactionsFromFile(fileKey)
                    
                    // Then
                    result.shouldBeEmpty()
                }
            }
            
            context("when JSON parsing fails") {
                it("should return empty list and log error") {
                    // Given
                    val fileKey = "transactions/2025/10/14/invalid.json"
                    val jsonContent = """{"invalid": "json"}"""
                    
                    val mockResponse = mockk<ResponseInputStream<GetObjectResponse>> {
                        every { readAllBytes() } returns jsonContent.toByteArray()
                    }
                    
                    every { s3Client.getObject(any<GetObjectRequest>()) } returns mockResponse
                    every { objectMapper.readValue<Transaction>(any<String>()) } throws RuntimeException("JSON error")
                    
                    // When
                    val result = s3Service.readTransactionsFromFile(fileKey)
                    
                    // Then
                    result.shouldBeEmpty()
                }
            }
        }

        describe("moveToRejected") {
            
            context("when move operation succeeds") {
                it("should copy file to rejected bucket and delete from input bucket") {
                    // Given
                    val fileKey = "transactions/2025/10/14/test.json"
                    val reason = "Invalid format"
                    
                    every { s3Client.copyObject(any<CopyObjectRequest>()) } returns mockk()
                    every { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns mockk()
                    
                    // When
                    s3Service.moveToRejected(fileKey, reason)
                    
                    // Then
                    verify {
                        s3Client.copyObject(
                            match<CopyObjectRequest> { 
                                it.sourceBucket() == "test-input-bucket" &&
                                it.sourceKey() == fileKey &&
                                it.destinationBucket() == "test-rejected-bucket" &&
                                it.destinationKey() == "rejected/test.json" &&
                                it.metadata()["rejection-reason"] == reason
                            }
                        )
                    }
                    
                    verify {
                        s3Client.deleteObject(
                            match<DeleteObjectRequest> { 
                                it.bucket() == "test-input-bucket" &&
                                it.key() == fileKey
                            }
                        )
                    }
                }
            }
            
            context("when copy operation fails") {
                it("should throw exception") {
                    // Given
                    val fileKey = "transactions/2025/10/14/test.json"
                    val reason = "Invalid format"
                    
                    every { s3Client.copyObject(any<CopyObjectRequest>()) } throws RuntimeException("Copy failed")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        s3Service.moveToRejected(fileKey, reason)
                    }
                    
                    verify(exactly = 0) { s3Client.deleteObject(any()) }
                }
            }
            
            context("when delete operation fails") {
                it("should throw exception") {
                    // Given
                    val fileKey = "transactions/2025/10/14/test.json"
                    val reason = "Invalid format"
                    
                    every { s3Client.copyObject(any<CopyObjectRequest>()) } returns mockk()
                    every { s3Client.deleteObject(any<DeleteObjectRequest>()) } throws RuntimeException("Delete failed")
                    
                    // When & Then
                    shouldThrow<RuntimeException> {
                        s3Service.moveToRejected(fileKey, reason)
                    }
                }
            }
        }
    }
})
