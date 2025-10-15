package br.com.itau.etl.producer.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class FileProcessorService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var s3Service: S3Service

    @Autowired
    private lateinit var dynamoDbService: DynamoDbService

    @Autowired
    private lateinit var validationService: ValidationService

    @Autowired
    private lateinit var kafkaProducerService: KafkaProducerService

    @Scheduled(fixedDelayString = "\${app.scheduler.file-processing-delay:30000}")
    fun processFiles() {
        logger.info { "Starting file processing job" }

        try {
            val pendingFiles = s3Service.listPendingFiles()
            
            if (pendingFiles.isEmpty()) {
                logger.debug { "No pending files to process" }
                return
            }

            pendingFiles.forEach { fileKey ->
                processFile(fileKey)
            }

            logger.info { "File processing job completed. Processed ${pendingFiles.size} files" }
        } catch (e: Exception) {
            logger.error(e) { "Error during file processing job" }
        }
    }

    private fun processFile(fileKey: String) {
        try {
            // Check if file was already processed
            if (dynamoDbService.isFileProcessed(fileKey)) {
                logger.debug { "File $fileKey already processed, skipping" }
                return
            }

            logger.info { "Processing file: $fileKey" }

            // Read transactions from file
            val transactions = s3Service.readTransactionsFromFile(fileKey)
            
            if (transactions.isEmpty()) {
                logger.warn { "File $fileKey contains no valid transactions" }
                s3Service.moveToRejected(fileKey, "No valid transactions found")
                dynamoDbService.markFileAsProcessed(fileKey, "REJECTED")
                return
            }

            // Validate and process each transaction
            val validTransactions = mutableListOf<br.com.itau.etl.producer.model.Transaction>()
            val invalidCount = transactions.map { transaction ->
                val result = validationService.validate(transaction)
                if (result.isValid) {
                    validTransactions.add(transaction)
                    0
                } else {
                    logger.warn { "Invalid transaction ${transaction.id}: ${result.errors.joinToString(", ")}" }
                    1
                }
            }.sum()

            // If too many invalid transactions, reject the entire file
            val errorRate = invalidCount.toDouble() / transactions.size
            if (errorRate > 0.1) { // More than 10% error rate
                logger.error { "File $fileKey has high error rate ($errorRate), rejecting entire file" }
                s3Service.moveToRejected(fileKey, "High error rate: $errorRate")
                dynamoDbService.markFileAsProcessed(fileKey, "REJECTED")
                return
            }

            // Send valid transactions to Kafka
            if (validTransactions.isNotEmpty()) {
                kafkaProducerService.sendBatch(validTransactions)
                logger.info { "Sent ${validTransactions.size} valid transactions from file $fileKey to Kafka" }
            }

            // Mark file as processed
            dynamoDbService.markFileAsProcessed(fileKey, "PROCESSED")
            logger.info { "Successfully processed file $fileKey: ${validTransactions.size} valid, $invalidCount invalid transactions" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to process file $fileKey" }
            try {
                dynamoDbService.markFileAsProcessed(fileKey, "ERROR")
            } catch (dbError: Exception) {
                logger.error(dbError) { "Failed to mark file $fileKey as error" }
            }
        }
    }
}
