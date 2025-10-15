package br.com.itau.etl.producer.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Service
class DynamoDbService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Value("\${app.dynamodb.processed-files-table}")
    private lateinit var processedFilesTable: String

    fun isFileProcessed(fileKey: String): Boolean {
        return try {
            val request = GetItemRequest.builder()
                .tableName(processedFilesTable)
                .key(mapOf("file_key" to AttributeValue.builder().s(fileKey).build()))
                .build()

            val response = dynamoDbClient.getItem(request)
            val exists = response.hasItem()
            
            logger.debug { "File $fileKey processed status: $exists" }
            exists
        } catch (e: Exception) {
            logger.error(e) { "Failed to check if file $fileKey was processed" }
            false
        }
    }

    fun markFileAsProcessed(fileKey: String, status: String = "PROCESSED") {
        try {
            val item = mapOf(
                "file_key" to AttributeValue.builder().s(fileKey).build(),
                "status" to AttributeValue.builder().s(status).build(),
                "processed_at" to AttributeValue.builder().s(System.currentTimeMillis().toString()).build()
            )

            val request = PutItemRequest.builder()
                .tableName(processedFilesTable)
                .item(item)
                .build()

            dynamoDbClient.putItem(request)
            logger.info { "File $fileKey marked as $status" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to mark file $fileKey as processed" }
            throw e
        }
    }
}
