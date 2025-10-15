package br.com.itau.etl.consumer.service

import br.com.itau.etl.consumer.model.ProcessedTransaction
import br.com.itau.etl.consumer.model.Transaction
import br.com.itau.etl.consumer.model.TransactionStatus
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TransactionProcessorService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var dynamoDbService: DynamoDbService

    fun processTransaction(transaction: Transaction): ProcessedTransaction {
        try {
            // Check if transaction already exists (idempotency)
            if (dynamoDbService.transactionExists(transaction.id)) {
                logger.warn { "Transaction ${transaction.id} already exists, skipping processing" }
                throw IllegalStateException("Transaction already processed")
            }

            // Business logic processing can be added here
            // For example: fraud detection, balance checks, etc.
            
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

            // Save to DynamoDB
            dynamoDbService.saveTransaction(processedTransaction)
            
            logger.info { "Successfully processed transaction ${transaction.id}" }
            return processedTransaction
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to process transaction ${transaction.id}" }
            
            // Create failed transaction record
            val failedTransaction = ProcessedTransaction(
                transactionId = transaction.id,
                accountId = transaction.accountId,
                amount = transaction.amount,
                type = transaction.type,
                description = transaction.description,
                timestamp = transaction.timestamp,
                processedAt = LocalDateTime.now(),
                pixData = transaction.pixData,
                status = TransactionStatus.ERROR
            )
            
            try {
                dynamoDbService.saveTransaction(failedTransaction)
            } catch (dbError: Exception) {
                logger.error(dbError) { "Failed to save error status for transaction ${transaction.id}" }
            }
            
            throw e
        }
    }

    fun reprocessFailedTransaction(transactionId: String) {
        try {
            dynamoDbService.updateTransactionStatus(transactionId, TransactionStatus.PROCESSED)
            logger.info { "Successfully reprocessed failed transaction $transactionId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to reprocess transaction $transactionId" }
            throw e
        }
    }
}
