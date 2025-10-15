package br.com.itau.etl.consumer.service

import br.com.itau.etl.consumer.model.ProcessedTransaction
import br.com.itau.etl.consumer.model.TransactionStatus
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.format.DateTimeFormatter

@Service
class DynamoDbService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Value("\${app.dynamodb.transactions-table}")
    private lateinit var transactionsTable: String

    fun saveTransaction(transaction: ProcessedTransaction) {
        try {
            val item = mapOf(
                "transaction_id" to AttributeValue.builder().s(transaction.transactionId).build(),
                "account_id" to AttributeValue.builder().s(transaction.accountId).build(),
                "amount" to AttributeValue.builder().n(transaction.amount.toString()).build(),
                "type" to AttributeValue.builder().s(transaction.type.name).build(),
                "description" to AttributeValue.builder().s(transaction.description).build(),
                "timestamp" to AttributeValue.builder().s(transaction.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build(),
                "processed_at" to AttributeValue.builder().s(transaction.processedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build(),
                "status" to AttributeValue.builder().s(transaction.status.name).build(),
                "pix_key" to transaction.pixData?.let { AttributeValue.builder().s(it.pixKey).build() },
                "pix_key_type" to transaction.pixData?.let { AttributeValue.builder().s(it.pixKeyType.name).build() },
                "end_to_end_id" to transaction.pixData?.let { AttributeValue.builder().s(it.endToEndId).build() }
            ).filterValues { it != null } as Map<String, AttributeValue>

            val request = PutItemRequest.builder()
                .tableName(transactionsTable)
                .item(item)
                .build()

            dynamoDbClient.putItem(request)
            logger.info { "Transaction ${transaction.transactionId} saved to DynamoDB" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save transaction ${transaction.transactionId} to DynamoDB" }
            throw e
        }
    }

    fun transactionExists(transactionId: String): Boolean {
        return try {
            val request = GetItemRequest.builder()
                .tableName(transactionsTable)
                .key(mapOf("transaction_id" to AttributeValue.builder().s(transactionId).build()))
                .build()

            val response = dynamoDbClient.getItem(request)
            val exists = response.hasItem()
            
            logger.debug { "Transaction $transactionId existence check: $exists" }
            exists
        } catch (e: Exception) {
            logger.error(e) { "Failed to check existence of transaction $transactionId" }
            false
        }
    }

    fun updateTransactionStatus(transactionId: String, status: TransactionStatus) {
        try {
            val request = UpdateItemRequest.builder()
                .tableName(transactionsTable)
                .key(mapOf("transaction_id" to AttributeValue.builder().s(transactionId).build()))
                .updateExpression("SET #status = :status, updated_at = :updated_at")
                .expressionAttributeNames(mapOf("#status" to "status"))
                .expressionAttributeValues(mapOf(
                    ":status" to AttributeValue.builder().s(status.name).build(),
                    ":updated_at" to AttributeValue.builder().s(System.currentTimeMillis().toString()).build()
                ))
                .build()

            dynamoDbClient.updateItem(request)
            logger.info { "Transaction $transactionId status updated to $status" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update status of transaction $transactionId to $status" }
            throw e
        }
    }
}
