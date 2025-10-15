package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.Transaction
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

@Service
class S3Service {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var s3Client: S3Client

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${app.s3.input-bucket}")
    private lateinit var inputBucket: String

    @Value("\${app.s3.rejected-bucket}")
    private lateinit var rejectedBucket: String

    fun listPendingFiles(): List<String> {
        return try {
            val request = ListObjectsV2Request.builder()
                .bucket(inputBucket)
                .prefix("transactions/")
                .build()

            val response = s3Client.listObjectsV2(request)
            val fileKeys = response.contents().map { it.key() }
            
            logger.info { "Found ${fileKeys.size} files in input bucket" }
            fileKeys
        } catch (e: Exception) {
            logger.error(e) { "Failed to list files from S3 bucket $inputBucket" }
            emptyList()
        }
    }

    fun readTransactionsFromFile(fileKey: String): List<Transaction> {
        return try {
            val request = GetObjectRequest.builder()
                .bucket(inputBucket)
                .key(fileKey)
                .build()

            val response: ResponseInputStream<GetObjectResponse> = s3Client.getObject(request)
            val content = response.readAllBytes().toString(Charsets.UTF_8)
            
            // Assume JSON Lines format (one JSON object per line)
            val transactions = content.lines()
                .filter { it.isNotBlank() }
                .map { line -> objectMapper.readValue<Transaction>(line) }
            
            logger.info { "Read ${transactions.size} transactions from file $fileKey" }
            transactions
        } catch (e: Exception) {
            logger.error(e) { "Failed to read transactions from file $fileKey" }
            emptyList()
        }
    }

    fun moveToRejected(fileKey: String, reason: String) {
        try {
            val fileName = fileKey.substringAfterLast("/")
            val rejectedKey = "rejected/$fileName"

            // Copy to rejected bucket
            val copyRequest = CopyObjectRequest.builder()
                .sourceBucket(inputBucket)
                .sourceKey(fileKey)
                .destinationBucket(rejectedBucket)
                .destinationKey(rejectedKey)
                .metadata(mapOf("rejection-reason" to reason))
                .build()

            s3Client.copyObject(copyRequest)

            // Delete from input bucket
            val deleteRequest = DeleteObjectRequest.builder()
                .bucket(inputBucket)
                .key(fileKey)
                .build()

            s3Client.deleteObject(deleteRequest)
            
            logger.info { "File $fileKey moved to rejected bucket with reason: $reason" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to move file $fileKey to rejected bucket" }
            throw e
        }
    }
}
