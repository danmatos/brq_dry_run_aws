package br.com.itau.etl.aggregator.service

import br.com.itau.etl.aggregator.model.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Service
class TransactionAggregatorService {

    private val logger = KotlinLogging.logger {}
    
    // In-memory storage for aggregation (in production, consider using Redis or similar)
    private val transactionBuffer = ConcurrentHashMap<String, MutableList<Transaction>>()

    fun addTransaction(transaction: Transaction) {
        val period = getCurrentPeriod()
        transactionBuffer.computeIfAbsent(period) { mutableListOf() }.add(transaction)
        
        logger.debug { "Added transaction ${transaction.id} to period $period. Buffer size: ${transactionBuffer[period]?.size}" }
    }

    fun generateSummary(period: String = getCurrentPeriod()): TransactionSummary? {
        val transactions = transactionBuffer[period]
        
        if (transactions.isNullOrEmpty()) {
            logger.debug { "No transactions found for period $period" }
            return null
        }

        logger.info { "Generating summary for period $period with ${transactions.size} transactions" }

        val totalTransactions = transactions.size.toLong()
        val totalAmount = transactions.sumOf { it.amount }

        // Group by transaction type
        val transactionsByType = transactions
            .groupBy { it.type }
            .mapValues { (_, txns) ->
                val count = txns.size.toLong()
                val sum = txns.sumOf { it.amount }
                val average = if (count > 0) sum.divide(BigDecimal(count), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
                TransactionTypeStats(count, sum, average)
            }

        // Top accounts by transaction volume
        val topAccounts = transactions
            .groupBy { it.accountId }
            .map { (accountId, txns) ->
                AccountStats(
                    accountId = accountId,
                    transactionCount = txns.size.toLong(),
                    totalAmount = txns.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(10)

        // PIX statistics
        val pixTransactions = transactions.filter { it.type == TransactionType.PIX }
        val pixStats = PixStats(
            totalPixTransactions = pixTransactions.size.toLong(),
            totalPixAmount = pixTransactions.sumOf { it.amount },
            pixByKeyType = pixTransactions
                .mapNotNull { it.pixData?.pixKeyType }
                .groupingBy { it }
                .eachCount()
                .mapValues { it.value.toLong() }
        )

        val summary = TransactionSummary(
            period = period,
            totalTransactions = totalTransactions,
            totalAmount = totalAmount,
            transactionsByType = transactionsByType,
            topAccounts = topAccounts,
            pixStats = pixStats,
            generatedAt = LocalDateTime.now()
        )

        logger.info { "Generated summary for period $period: $totalTransactions transactions, total amount: $totalAmount" }
        return summary
    }

    fun clearPeriod(period: String) {
        transactionBuffer.remove(period)
        logger.info { "Cleared transaction buffer for period $period" }
    }

    fun getCurrentPeriod(): String {
        // Generate period based on current hour (hourly aggregation)
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
    }

    fun getPreviousPeriod(): String {
        // Get previous hour period
        return LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
    }

    fun getBufferStatus(): Map<String, Int> {
        return transactionBuffer.mapValues { it.value.size }
    }
}
