package br.com.itau.etl.aggregator.service

import br.com.itau.etl.aggregator.model.TransactionSummary
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ReportService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var s3Client: S3Client

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${app.s3.reports-bucket}")
    private lateinit var reportsBucket: String

    fun saveReport(summary: TransactionSummary) {
        try {
            val reportJson = objectMapper.writeValueAsString(summary)
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"))
            val fileName = "transaction-summary-${summary.period}.json"
            val key = "reports/$timestamp/$fileName"

            val request = PutObjectRequest.builder()
                .bucket(reportsBucket)
                .key(key)
                .contentType("application/json")
                .metadata(mapOf(
                    "report-type" to "transaction-summary",
                    "period" to summary.period,
                    "generated-at" to summary.generatedAt.toString(),
                    "total-transactions" to summary.totalTransactions.toString(),
                    "total-amount" to summary.totalAmount.toString()
                ))
                .build()

            s3Client.putObject(request, RequestBody.fromString(reportJson))
            
            logger.info { "Successfully saved report for period ${summary.period} to S3: $key" }
            
            // Also save a summary CSV for easy analysis
            saveCsvSummary(summary, timestamp)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to save report for period ${summary.period}" }
            throw e
        }
    }

    private fun saveCsvSummary(summary: TransactionSummary, timestamp: String) {
        try {
            val csvContent = generateCsvSummary(summary)
            val fileName = "transaction-summary-${summary.period}.csv"
            val key = "reports/$timestamp/$fileName"

            val request = PutObjectRequest.builder()
                .bucket(reportsBucket)
                .key(key)
                .contentType("text/csv")
                .build()

            s3Client.putObject(request, RequestBody.fromString(csvContent))
            
            logger.info { "Successfully saved CSV report for period ${summary.period} to S3: $key" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to save CSV report for period ${summary.period}" }
        }
    }

    private fun generateCsvSummary(summary: TransactionSummary): String {
        val csv = StringBuilder()
        
        // Header
        csv.appendLine("metric,value")
        
        // Basic metrics
        csv.appendLine("period,${summary.period}")
        csv.appendLine("total_transactions,${summary.totalTransactions}")
        csv.appendLine("total_amount,${summary.totalAmount}")
        csv.appendLine("generated_at,${summary.generatedAt}")
        
        // Transaction type breakdown
        summary.transactionsByType.forEach { (type, stats) ->
            csv.appendLine("${type.name.lowercase()}_count,${stats.count}")
            csv.appendLine("${type.name.lowercase()}_amount,${stats.totalAmount}")
            csv.appendLine("${type.name.lowercase()}_average,${stats.averageAmount}")
        }
        
        // PIX stats
        csv.appendLine("pix_total_transactions,${summary.pixStats.totalPixTransactions}")
        csv.appendLine("pix_total_amount,${summary.pixStats.totalPixAmount}")
        
        summary.pixStats.pixByKeyType.forEach { (keyType, count) ->
            csv.appendLine("pix_${keyType.name.lowercase()}_count,$count")
        }
        
        return csv.toString()
    }

    fun saveDetailedReport(summary: TransactionSummary) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"))
            
            // Detailed breakdown by account
            val accountsContent = StringBuilder()
            accountsContent.appendLine("account_id,transaction_count,total_amount")
            summary.topAccounts.forEach { account ->
                accountsContent.appendLine("${account.accountId},${account.transactionCount},${account.totalAmount}")
            }
            
            val accountsKey = "reports/$timestamp/top-accounts-${summary.period}.csv"
            val request = PutObjectRequest.builder()
                .bucket(reportsBucket)
                .key(accountsKey)
                .contentType("text/csv")
                .build()

            s3Client.putObject(request, RequestBody.fromString(accountsContent.toString()))
            
            logger.info { "Successfully saved detailed accounts report for period ${summary.period}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to save detailed report for period ${summary.period}" }
        }
    }
}
