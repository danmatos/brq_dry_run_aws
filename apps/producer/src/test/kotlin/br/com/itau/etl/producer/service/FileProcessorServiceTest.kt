package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.ProcessingResult
import br.com.itau.etl.producer.model.Transaction
import br.com.itau.etl.producer.model.TransactionType
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

class FileProcessorServiceTest : DescribeSpec({

    describe("FileProcessorService") {
        val s3Service = mockk<S3Service>()
        val dynamoDbService = mockk<DynamoDbService>()
        val validationService = mockk<ValidationService>()
        val kafkaProducerService = mockk<KafkaProducerService>()
        val fileProcessorService = FileProcessorService()
        
        beforeEach {
            ReflectionTestUtils.setField(fileProcessorService, "s3Service", s3Service)
            ReflectionTestUtils.setField(fileProcessorService, "dynamoDbService", dynamoDbService)
            ReflectionTestUtils.setField(fileProcessorService, "validationService", validationService)
            ReflectionTestUtils.setField(fileProcessorService, "kafkaProducerService", kafkaProducerService)
            clearAllMocks()
        }

        describe("processFiles") {
            
            context("when there are no pending files") {
                it("should complete without processing") {
                    // Given
                    every { s3Service.listPendingFiles() } returns emptyList()
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { s3Service.listPendingFiles() }
                    verify(exactly = 0) { s3Service.readTransactionsFromFile(any()) }
                }
            }
            
            context("when there are pending files") {
                it("should process all pending files") {
                    // Given
                    val fileKeys = listOf("file1.json", "file2.json")
                    every { s3Service.listPendingFiles() } returns fileKeys
                    
                    // Mock file processing dependencies
                    every { dynamoDbService.isFileProcessed(any()) } returns false
                    every { s3Service.readTransactionsFromFile(any()) } returns emptyList()
                    every { s3Service.moveToRejected(any(), any()) } just Runs
                    every { dynamoDbService.markFileAsProcessed(any(), any()) } just Runs
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { s3Service.listPendingFiles() }
                    verify(exactly = 2) { dynamoDbService.isFileProcessed(any()) }
                    verify(exactly = 2) { s3Service.readTransactionsFromFile(any()) }
                }
            }
            
            context("when an exception occurs during processing") {
                it("should log error but continue") {
                    // Given
                    every { s3Service.listPendingFiles() } throws RuntimeException("S3 error")
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { s3Service.listPendingFiles() }
                    // Should not crash the application
                }
            }
        }

        describe("processFile") {
            val fileKey = "test-file.json"
            
            context("when file was already processed") {
                it("should skip processing") {
                    // Given
                    every { dynamoDbService.isFileProcessed(fileKey) } returns true
                    
                    // When
                    fileProcessorService.processFiles()
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { dynamoDbService.isFileProcessed(fileKey) }
                    verify(exactly = 0) { s3Service.readTransactionsFromFile(fileKey) }
                }
            }
            
            context("when file contains no valid transactions") {
                it("should move file to rejected bucket") {
                    // Given
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    every { dynamoDbService.isFileProcessed(fileKey) } returns false
                    every { s3Service.readTransactionsFromFile(fileKey) } returns emptyList()
                    every { s3Service.moveToRejected(fileKey, "No valid transactions found") } just Runs
                    every { dynamoDbService.markFileAsProcessed(fileKey, "REJECTED") } just Runs
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { s3Service.readTransactionsFromFile(fileKey) }
                    verify { s3Service.moveToRejected(fileKey, "No valid transactions found") }
                    verify { dynamoDbService.markFileAsProcessed(fileKey, "REJECTED") }
                }
            }
            
            context("when file contains valid transactions") {
                it("should validate, send to Kafka, and mark as processed") {
                    // Given
                    val transaction1 = Transaction(
                        id = "TXN-001",
                        accountId = "ACC-001",
                        amount = BigDecimal("100.50"),
                        type = TransactionType.CREDIT,
                        description = "Valid transaction 1",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transaction2 = Transaction(
                        id = "TXN-002",
                        accountId = "ACC-002",
                        amount = BigDecimal("250.75"),
                        type = TransactionType.DEBIT,
                        description = "Valid transaction 2",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transactions = listOf(transaction1, transaction2)
                    
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    every { dynamoDbService.isFileProcessed(fileKey) } returns false
                    every { s3Service.readTransactionsFromFile(fileKey) } returns transactions
                    
                    every { validationService.validate(transaction1) } returns ProcessingResult(transaction1, true, emptyList())
                    every { validationService.validate(transaction2) } returns ProcessingResult(transaction2, true, emptyList())
                    
                    every { kafkaProducerService.sendBatch(transactions) } just Runs
                    every { dynamoDbService.markFileAsProcessed(fileKey, "PROCESSED") } just Runs
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { validationService.validate(transaction1) }
                    verify { validationService.validate(transaction2) }
                    verify { kafkaProducerService.sendBatch(transactions) }
                    verify { dynamoDbService.markFileAsProcessed(fileKey, "PROCESSED") }
                }
            }
            
            context("when file has some invalid transactions within acceptable error rate") {
                it("should send valid transactions and mark as processed") {
                    // Given
                    val validTransaction = Transaction(
                        id = "TXN-001",
                        accountId = "ACC-001",
                        amount = BigDecimal("100.50"),
                        type = TransactionType.CREDIT,
                        description = "Valid transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val invalidTransaction = Transaction(
                        id = "TXN-002",
                        accountId = "",  // Invalid account ID
                        amount = BigDecimal("250.75"),
                        type = TransactionType.DEBIT,
                        description = "Invalid transaction",
                        timestamp = LocalDateTime.now()
                    )
                    
                    val transactions = listOf(validTransaction, invalidTransaction)
                    
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    every { dynamoDbService.isFileProcessed(fileKey) } returns false
                    every { s3Service.readTransactionsFromFile(fileKey) } returns transactions
                    
                    every { validationService.validate(validTransaction) } returns ProcessingResult(validTransaction, true, emptyList())
                    every { validationService.validate(invalidTransaction) } returns ProcessingResult(invalidTransaction, false, listOf("Account ID cannot be blank"))
                    
                    every { kafkaProducerService.sendBatch(listOf(validTransaction)) } just Runs
                    every { dynamoDbService.markFileAsProcessed(fileKey, "PROCESSED") } just Runs
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { kafkaProducerService.sendBatch(listOf(validTransaction)) }
                    verify { dynamoDbService.markFileAsProcessed(fileKey, "PROCESSED") }
                }
            }
            
            context("when file has high error rate") {
                it("should reject entire file") {
                    // Given - Creating 11 invalid transactions out of 11 total (100% error rate)
                    val invalidTransactions = (1..11).map { i ->
                        Transaction(
                            id = "TXN-$i",
                            accountId = "",  // All invalid
                            amount = BigDecimal("100.00"),
                            type = TransactionType.CREDIT,
                            description = "Invalid transaction $i",
                            timestamp = LocalDateTime.now()
                        )
                    }
                    
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    every { dynamoDbService.isFileProcessed(fileKey) } returns false
                    every { s3Service.readTransactionsFromFile(fileKey) } returns invalidTransactions
                    
                    invalidTransactions.forEach { transaction ->
                        every { validationService.validate(transaction) } returns ProcessingResult(
                            transaction, false, listOf("Account ID cannot be blank")
                        )
                    }
                    
                    every { s3Service.moveToRejected(fileKey, any()) } just Runs
                    every { dynamoDbService.markFileAsProcessed(fileKey, "REJECTED") } just Runs
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { s3Service.moveToRejected(fileKey, match { it.contains("High error rate") }) }
                    verify { dynamoDbService.markFileAsProcessed(fileKey, "REJECTED") }
                    verify(exactly = 0) { kafkaProducerService.sendBatch(any()) }
                }
            }
            
            context("when an exception occurs during file processing") {
                it("should mark file as error") {
                    // Given
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    every { dynamoDbService.isFileProcessed(fileKey) } returns false
                    every { s3Service.readTransactionsFromFile(fileKey) } throws RuntimeException("Processing error")
                    every { dynamoDbService.markFileAsProcessed(fileKey, "ERROR") } just Runs
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { dynamoDbService.markFileAsProcessed(fileKey, "ERROR") }
                }
            }
            
            context("when marking file as error also fails") {
                it("should log error but not crash") {
                    // Given
                    every { s3Service.listPendingFiles() } returns listOf(fileKey)
                    every { dynamoDbService.isFileProcessed(fileKey) } returns false
                    every { s3Service.readTransactionsFromFile(fileKey) } throws RuntimeException("Processing error")
                    every { dynamoDbService.markFileAsProcessed(fileKey, "ERROR") } throws RuntimeException("DB error")
                    
                    // When
                    fileProcessorService.processFiles()
                    
                    // Then
                    verify { dynamoDbService.markFileAsProcessed(fileKey, "ERROR") }
                    // Should not crash the application
                }
            }
        }
    }
})
