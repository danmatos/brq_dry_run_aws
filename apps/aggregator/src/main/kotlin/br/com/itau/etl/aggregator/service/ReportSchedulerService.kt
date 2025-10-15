package br.com.itau.etl.aggregator.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ReportSchedulerService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var transactionAggregatorService: TransactionAggregatorService

    @Autowired
    private lateinit var reportService: ReportService

    @Scheduled(cron = "\${app.scheduler.report-generation-cron:0 5 * * * *}") // Every hour at 5 minutes past
    fun generateHourlyReport() {
        logger.info { "Starting hourly report generation" }

        try {
            val previousPeriod = transactionAggregatorService.getPreviousPeriod()
            val summary = transactionAggregatorService.generateSummary(previousPeriod)

            if (summary != null) {
                // Save reports to S3
                reportService.saveReport(summary)
                reportService.saveDetailedReport(summary)
                
                // Clear the processed period from memory
                transactionAggregatorService.clearPeriod(previousPeriod)
                
                logger.info { "Successfully generated and saved reports for period $previousPeriod" }
            } else {
                logger.info { "No transactions found for period $previousPeriod, skipping report generation" }
            }

            // Log buffer status
            val bufferStatus = transactionAggregatorService.getBufferStatus()
            logger.info { "Current buffer status: $bufferStatus" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate hourly report" }
        }
    }

    @Scheduled(fixedDelayString = "\${app.scheduler.status-log-delay:300000}") // Every 5 minutes
    fun logStatus() {
        try {
            val bufferStatus = transactionAggregatorService.getBufferStatus()
            val totalTransactions = bufferStatus.values.sum()
            
            if (totalTransactions > 0) {
                logger.info { "Aggregator status - Total buffered transactions: $totalTransactions, Periods: ${bufferStatus.keys}" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to log aggregator status" }
        }
    }
}
